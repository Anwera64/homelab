"""
title: Model Memory Loading Indicator
author: homelab
description: Displays a 'Loading model into memory...' UI indicator while the LLM model loads into GPU memory during cold starts.
version: 1.0.0
"""

from pydantic import BaseModel, Field
from typing import Optional, Callable, Awaitable, Any


class Filter:
    class Valves(BaseModel):
        priority: int = Field(
            default=0,
            description="Priority level for filter execution."
        )
        status_message: str = Field(
            default="Loading model into memory...",
            description="Status message displayed while model is loading into memory."
        )

    def __init__(self):
        self.valves = self.Valves()
        self._active_requests = set()
        self._pending_unkeyed = 0

    async def inlet(
        self,
        body: dict,
        __event_emitter__: Optional[Callable[[dict], Awaitable[None]]] = None,
        __message_id__: Optional[str] = None,
    ) -> dict:
        if __event_emitter__:
            if __message_id__:
                self._active_requests.add(__message_id__)
            else:
                self._pending_unkeyed += 1

            await __event_emitter__(
                {
                    "type": "status",
                    "data": {
                        "description": self.valves.status_message,
                        "done": False,
                        "hidden": False,
                    },
                }
            )
        return body

    async def stream(
        self,
        event: dict,
        __event_emitter__: Optional[Callable[[dict], Awaitable[None]]] = None,
        __message_id__: Optional[str] = None,
    ) -> dict:
        need_dismiss = False
        if __message_id__:
            if __message_id__ in self._active_requests:
                self._active_requests.discard(__message_id__)
                need_dismiss = True
        elif self._pending_unkeyed > 0:
            self._pending_unkeyed -= 1
            need_dismiss = True

        if need_dismiss and __event_emitter__:
            await __event_emitter__(
                {
                    "type": "status",
                    "data": {
                        "description": self.valves.status_message,
                        "done": True,
                        "hidden": True,
                    },
                }
            )
        return event

    async def outlet(
        self,
        body: dict,
        __event_emitter__: Optional[Callable[[dict], Awaitable[None]]] = None,
        __message_id__: Optional[str] = None,
    ) -> dict:
        need_dismiss = False
        if __message_id__:
            if __message_id__ in self._active_requests:
                self._active_requests.discard(__message_id__)
                need_dismiss = True
        elif self._pending_unkeyed > 0:
            self._pending_unkeyed -= 1
            need_dismiss = True

        if need_dismiss and __event_emitter__:
            await __event_emitter__(
                {
                    "type": "status",
                    "data": {
                        "description": self.valves.status_message,
                        "done": True,
                        "hidden": True,
                    },
                }
            )
        return body
