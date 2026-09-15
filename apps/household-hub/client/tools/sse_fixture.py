#!/usr/bin/env python3
"""SSE fixture server for the iOS (Darwin/NSURLSession) streaming proofs.

Kotlin/Native has no in-process `ServerSocket` equivalent, so the iOS SSE tests need an
external server. This is it: standard library only, Python 3, started and stopped by the
Gradle tasks in `shared/build.gradle.kts`.

It speaks just enough of the hub's chat API for `SessionRepositoryImpl.streamChatTurn`,
which POSTs to `/api/v1/sessions/<id>/chat/stream`. The `<id>` selects a scenario:

  lockstep    Writes delta 1, then REFUSES to write delta 2 until the client has told us,
              over a second connection (`GET /fixture/ack/lockstep/1`), that delta 1 really
              arrived. Same again for delta 3. The server therefore cannot physically have
              put delta N+1 on the wire before the client saw delta N, so a passing run is
              causal proof of incremental delivery rather than an inference from timing.
              After each ack the server also sleeps `--gap-millis` so the client can assert
              a real wall-clock gap between deltas on top of the handshake.

  long-pause  Writes delta 1, waits for its ack, then goes quiet for `--pause-seconds`
              (default 75) before writing delta 2. NSURLSession's *default*
              `timeoutIntervalForRequest` is 60s and measures the gap between bytes, so a
              pause longer than that kills the stream unless the app has raised it. This is
              the scenario that proves `PlatformModule.ios.kt`'s 3600s setting matters; it
              is deliberately expensive and only the slow Gradle task asks for it.

Endpoints:
  GET  /fixture/health            -> 200 "ok"           (readiness probe for Gradle)
  GET  /fixture/ack/<scen>/<n>    -> 200 "acked"        (client confirms delta <n> arrived)
  POST /api/v1/sessions/<scen>/chat/stream -> text/event-stream

Usage:
  python3 sse_fixture.py --port 8749 [--pid-file P] [--ready-file R]
                        [--pause-seconds 75] [--gap-millis 400] [--ack-timeout-seconds 30]
"""

import argparse
import json
import os
import signal
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SCENARIO_LOCKSTEP = "lockstep"
SCENARIO_LONG_PAUSE = "long-pause"

# Filled in by main(); read by the handler threads.
OPTIONS = None


class AckRegistry:
    """One `threading.Event` per (scenario, delta index), created on first touch.

    A scenario resets its events when its stream starts, so the server survives being
    reused by several test binaries over its lifetime.
    """

    def __init__(self):
        self._lock = threading.Lock()
        self._events = {}

    def event(self, scenario, index):
        with self._lock:
            return self._events.setdefault(scenario, {}).setdefault(index, threading.Event())

    def reset(self, scenario):
        with self._lock:
            self._events[scenario] = {}


ACKS = AckRegistry()


class SseFixtureHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    # ---- plumbing ---------------------------------------------------------------

    def log_message(self, fmt, *args):
        now = time.time()
        stamp = time.strftime("%H:%M:%S", time.localtime(now)) + ".%03d" % int((now % 1) * 1000)
        sys.stderr.write("[sse-fixture %s] %s\n" % (stamp, fmt % args))

    def _plain(self, status, body):
        payload = body if isinstance(body, bytes) else body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _begin_sse(self):
        # No Content-Length and no chunking: the body ends when we close the socket. The
        # client must therefore surface bytes as they land rather than waiting for a length.
        self.close_connection = True
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.flush()

    def _emit(self, obj):
        self.wfile.write(("data: " + json.dumps(obj) + "\n\n").encode("utf-8"))
        self.wfile.flush()

    def _emit_raw(self, line):
        self.wfile.write(("data: " + line + "\n\n").encode("utf-8"))
        self.wfile.flush()

    def _delta(self, content):
        self._emit({"type": "delta", "content": content})

    def _finish_stream(self, message_id, assistant_content):
        self._emit(
            {
                "type": "done",
                "message_id": message_id,
                "assistant_content": assistant_content,
                "agent_name": "Fixture",
            }
        )
        self._emit_raw("[DONE]")

    def _await_ack(self, scenario, index):
        """Block until the client confirms delta `index`. Returns False on timeout.

        On timeout we push a sentinel delta down the stream so that a buffering engine
        produces a loud, readable assertion failure on the client instead of a bare hang.
        """
        timeout = OPTIONS.ack_timeout_seconds
        if ACKS.event(scenario, index).wait(timeout):
            return True
        self.log_message(
            "no ack for %s delta %d within %.0fs - client never saw it (engine buffering?)",
            scenario,
            index,
            timeout,
        )
        self._delta("MISSING-ACK-FOR-DELTA-%d" % index)
        return False

    # ---- routes -----------------------------------------------------------------

    def do_GET(self):
        if self.path == "/fixture/health":
            self._plain(200, "ok")
            return
        if self.path.startswith("/fixture/ack/"):
            parts = self.path[len("/fixture/ack/"):].split("/")
            if len(parts) == 2 and parts[1].isdigit():
                ACKS.event(parts[0], int(parts[1])).set()
                self.log_message("ack %s delta %s", parts[0], parts[1])
                self._plain(200, "acked")
                return
            self._plain(400, "bad ack path")
            return
        self._plain(404, "no such fixture route: " + self.path)

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)

        parts = self.path.strip("/").split("/")
        if len(parts) == 6 and parts[0:3] == ["api", "v1", "sessions"] and parts[4:6] == ["chat", "stream"]:
            scenario = parts[3]
            try:
                if scenario == SCENARIO_LOCKSTEP:
                    self._stream_lockstep()
                    return
                if scenario == SCENARIO_LONG_PAUSE:
                    self._stream_long_pause()
                    return
            except (BrokenPipeError, ConnectionResetError) as exc:
                # The client gave up (a timeout would look exactly like this). Nothing left
                # to write; let the test's own assertion report the failure.
                self.log_message("client went away mid-stream: %s", exc)
                self.close_connection = True
                return
        self._plain(404, "no such fixture route: " + self.path)

    # ---- scenarios --------------------------------------------------------------

    def _stream_lockstep(self):
        ACKS.reset(SCENARIO_LOCKSTEP)
        gap = OPTIONS.gap_millis / 1000.0
        self._begin_sse()
        self.log_message("lockstep: stream opened")
        for index, content in enumerate(["alpha ", "beta ", "gamma"], start=1):
            self._delta(content)
            self.log_message("lockstep: wrote delta %d (%r), waiting for ack", index, content)
            if not self._await_ack(SCENARIO_LOCKSTEP, index):
                self._finish_stream("msg-lockstep", "aborted")
                return
            time.sleep(gap)
        self._finish_stream("msg-lockstep", "alpha beta gamma")
        self.log_message("lockstep: stream done")

    def _stream_long_pause(self):
        ACKS.reset(SCENARIO_LONG_PAUSE)
        self._begin_sse()
        self.log_message("long-pause: stream opened")
        self._delta("before-pause ")
        if not self._await_ack(SCENARIO_LONG_PAUSE, 1):
            self._finish_stream("msg-long-pause", "aborted")
            return
        self.log_message("long-pause: going quiet for %.0fs", OPTIONS.pause_seconds)
        time.sleep(OPTIONS.pause_seconds)
        self._delta("after-pause")
        self._finish_stream("msg-long-pause", "before-pause after-pause")
        self.log_message("long-pause: stream done")


def main():
    global OPTIONS
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--pause-seconds", type=float, default=75.0)
    parser.add_argument("--gap-millis", type=int, default=400)
    parser.add_argument("--ack-timeout-seconds", type=float, default=30.0)
    parser.add_argument("--pid-file")
    parser.add_argument("--ready-file")
    OPTIONS = parser.parse_args()

    try:
        # allow_reuse_address only relaxes TIME_WAIT; a live listener still gets EADDRINUSE.
        httpd = ThreadingHTTPServer(("127.0.0.1", OPTIONS.port), SseFixtureHandler)
    except OSError as exc:
        sys.stderr.write(
            "\n"
            "!!! sse_fixture.py could NOT bind 127.0.0.1:%d - %s\n"
            "!!! Something is already listening on that port. Find it with:\n"
            "!!!     lsof -nP -iTCP:%d -sTCP:LISTEN\n"
            "!!! A fixture leaked by an interrupted test run is the usual cause.\n"
            "!!! Kill it and re-run; do NOT change the port, the Kotlin tests hardcode it.\n"
            "\n" % (OPTIONS.port, exc, OPTIONS.port)
        )
        sys.exit(2)

    httpd.daemon_threads = True

    if OPTIONS.pid_file:
        with open(OPTIONS.pid_file, "w") as handle:
            handle.write(str(os.getpid()))
    if OPTIONS.ready_file:
        with open(OPTIONS.ready_file, "w") as handle:
            handle.write("ready")

    def shutdown(_signum, _frame):
        threading.Thread(target=httpd.shutdown, daemon=True).start()

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    sys.stderr.write("[sse-fixture] listening on http://127.0.0.1:%d (pid %d)\n" % (OPTIONS.port, os.getpid()))
    sys.stderr.flush()
    try:
        httpd.serve_forever(poll_interval=0.2)
    finally:
        httpd.server_close()
        if OPTIONS.pid_file and os.path.exists(OPTIONS.pid_file):
            os.remove(OPTIONS.pid_file)


if __name__ == "__main__":
    main()
