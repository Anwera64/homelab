#!/usr/bin/env python3
"""
Register or update the Model Memory Loading Indicator filter in Open WebUI's database.
Works both on the host system (local SQLite) and inside/against the running Open WebUI Docker container.
"""

import json
import os
import sqlite3
import subprocess
import sys
import time

FILTER_ID = "model_loading_indicator"
FILTER_NAME = "Model Memory Loading Indicator"
FILTER_DESCRIPTION = "Displays a 'Loading model into memory...' UI indicator while the LLM model loads into GPU memory during cold starts."


def get_filter_paths():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    filter_file = os.path.join(base_dir, "filters", "model_loading_indicator.py")

    candidate_db_paths = [
        os.path.join(base_dir, "webui.db"),
        "/app/backend/data/webui.db",
        os.path.abspath(os.path.join(base_dir, "..", "open-webui", "webui.db")),
    ]

    local_db = None
    for candidate in candidate_db_paths:
        if os.path.isfile(candidate):
            local_db = candidate
            break

    return filter_file, local_db


def register_in_sqlite(db_path, filter_id, filter_name, content, meta_json):
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    now = int(time.time())

    try:
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='function'")
        if not cur.fetchone():
            return False, "Table 'function' does not exist in SQLite DB."

        user_id = None
        try:
            cur.execute("SELECT id FROM user WHERE role='admin' LIMIT 1")
            row = cur.fetchone()
            if row:
                user_id = row[0]
        except Exception:
            pass

        cur.execute("SELECT id FROM function WHERE id=?", (filter_id,))
        if cur.fetchone():
            cur.execute(
                """
                UPDATE function
                SET name=?, content=?, meta=?, is_active=1, is_global=1, updated_at=?
                WHERE id=?
                """,
                (filter_name, content, meta_json, now, filter_id),
            )
            action = "Updated"
        else:
            cur.execute(
                """
                INSERT INTO function (
                    id, user_id, name, type, content, meta, valves, is_active, is_global, updated_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    filter_id,
                    user_id,
                    filter_name,
                    "filter",
                    content,
                    meta_json,
                    None,
                    1,
                    1,
                    now,
                    now,
                ),
            )
            action = "Registered new"

        conn.commit()
        return True, f"[SUCCESS] {action} filter '{filter_id}' in {db_path} (active=True, global=True)."
    finally:
        conn.close()


def register_in_container(filter_id, filter_name, content, meta_json):
    python_snippet = """
import json, sqlite3, sys, time

payload = json.loads(sys.stdin.read())
conn = sqlite3.connect('/app/backend/data/webui.db')
cur = conn.cursor()
now = int(time.time())

try:
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='function'")
    if not cur.fetchone():
        print("[ERROR] Table 'function' does not exist in container DB.", file=sys.stderr)
        sys.exit(1)

    cur.execute("SELECT id FROM user WHERE role='admin' LIMIT 1")
    row = cur.fetchone()
    user_id = row[0] if row else None

    cur.execute("SELECT id FROM function WHERE id=?", (payload['id'],))
    if cur.fetchone():
        cur.execute(
            "UPDATE function SET name=?, content=?, meta=?, is_active=1, is_global=1, updated_at=? WHERE id=?",
            (payload['name'], payload['content'], payload['meta'], now, payload['id'])
        )
        action = "Updated"
    else:
        cur.execute(
            "INSERT INTO function (id, user_id, name, type, content, meta, valves, is_active, is_global, updated_at, created_at) "
            "VALUES (?, ?, ?, 'filter', ?, ?, NULL, 1, 1, ?, ?)",
            (payload['id'], user_id, payload['name'], payload['content'], payload['meta'], now, now)
        )
        action = "Registered new"

    conn.commit()
    print(f"[SUCCESS] {action} filter '{payload['id']}' in open-webui container (active=True, global=True).")
finally:
    conn.close()
"""
    try:
        proc = subprocess.run(
            ["docker", "exec", "-i", "open-webui", "python", "-c", python_snippet],
            input=json.dumps({
                "id": filter_id,
                "name": filter_name,
                "content": content,
                "meta": meta_json,
            }),
            text=True,
            capture_output=True,
            timeout=15,
        )
        return proc.returncode == 0, (proc.stdout or proc.stderr).strip()
    except Exception as e:
        return False, str(e)


def main():
    filter_file, local_db = get_filter_paths()

    if not os.path.isfile(filter_file):
        print(f"[ERROR] Filter file not found at: {filter_file}", file=sys.stderr)
        sys.exit(1)

    with open(filter_file, "r", encoding="utf-8") as f:
        filter_content = f.read()

    meta_json = json.dumps({
        "description": FILTER_DESCRIPTION,
        "manifest": {},
    })

    success_any = False

    # 1. Try registering in running Docker container if available
    is_docker_running = False
    try:
        check_proc = subprocess.run(
            ["docker", "inspect", "open-webui", "--format", "{{.State.Status}}"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        if check_proc.returncode == 0 and "running" in check_proc.stdout.strip():
            is_docker_running = True
    except Exception:
        pass

    if is_docker_running:
        c_ok, c_msg = register_in_container(FILTER_ID, FILTER_NAME, filter_content, meta_json)
        if c_ok:
            print(c_msg)
            success_any = True
        else:
            print(f"[WARNING] Container registration failed: {c_msg}", file=sys.stderr)

    # 2. Also register in local SQLite DB if found
    if local_db and os.path.isfile(local_db):
        l_ok, l_msg = register_in_sqlite(local_db, FILTER_ID, FILTER_NAME, filter_content, meta_json)
        if l_ok:
            print(l_msg)
            success_any = True
        else:
            print(f"[WARNING] Local DB registration failed: {l_msg}", file=sys.stderr)

    if not success_any:
        print("[ERROR] Could not register filter in either Docker container or local webui.db.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
