import asyncio
import logging
from datetime import datetime, timedelta, timezone
from typing import Callable, Dict, Optional

from app.domain.entities.session import ChatAwaitingReflection
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.model_residency import IModelResidency
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.turn_activity import ITurnActivity
from app.domain.use_cases.chat.tool_approval import pending_proposals
from app.domain.use_cases.memories.embed_memories import EmbedMemoriesUseCase
from app.domain.use_cases.memories.reflect_chat import ReflectChatUseCase
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase

logger = logging.getLogger(__name__)


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _aware(value: datetime) -> datetime:
    # SQLite gives datetimes back without their zone; every one the hub writes is UTC.
    return value if value.tzinfo else value.replace(tzinfo=timezone.utc)


class ReflectionSweepUseCase:
    """
    One pass of the reflection sweep (memory spec §2.2). Each chat with messages after its marker
    is reflected when it has gone quiet, its agent's model is already loaded, no turn is running
    anywhere on the hub, and no approval card waits in it. A chat that fails a condition is tried
    on a later pass. A turn that starts while a chat is reflected cancels it: nothing is written,
    the marker stays, and the pass ends.
    """

    def __init__(
        self,
        session_repo: ISessionRepository,
        agent_repo: IAgentRepository,
        model_resolver: ResolveAgentModelUseCase,
        residency: IModelResidency,
        turn_activity: ITurnActivity,
        reflect_chat: ReflectChatUseCase,
        embed_memories: EmbedMemoriesUseCase,
        idle_minutes: float = 10,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.session_repo = session_repo
        self.agent_repo = agent_repo
        self.model_resolver = model_resolver
        self.residency = residency
        self.turn_activity = turn_activity
        self.reflect_chat = reflect_chat
        self.embed_memories = embed_memories
        self.idle = timedelta(minutes=idle_minutes)
        self.clock = clock

    async def run_pass(self) -> None:
        # Memories saved while the embedder was down get their vectors here (memory spec §4.3).
        try:
            await self.embed_memories.fill_missing()
        except Exception as exc:
            logger.error("Filling memory vectors failed: %s", exc, exc_info=True)

        loaded: Dict[str, Optional[datetime]] = {}
        for chat in await self.session_repo.list_awaiting_reflection():
            if not await self._consider(chat, loaded):
                return

    def _log(self, chat: ChatAwaitingReflection, outcome: str, reason: str = "") -> None:
        logger.info(
            "Reflection sweep | member=%s session=%s outcome=%s%s",
            chat.session.user_id, chat.session.id, outcome, f" reason={reason}" if reason else "",
        )

    async def _consider(self, chat: ChatAwaitingReflection, loaded: Dict[str, Optional[datetime]]) -> bool:
        """Reflects or skips one chat; False when the pass should stop because a turn took over."""
        session = chat.session
        now = self.clock()
        newest = (await self.session_repo.get_messages(session.id, limit=1) or [None])[-1]
        if newest is None:
            return True

        if session.is_secret:
            # Nothing from a chat that is secret now (§2.2). The marker moves so what was said in
            # secret is never read, even once the chat is switched back.
            await self.session_repo.save_reflection(
                session.id, newest.id, now, {"added": [], "proposed": [], "retired": [], "at": now.isoformat()}
            )
            self._log(chat, "skipped", "secret")
            return True
        if now - _aware(chat.newest_message_at) < self.idle:
            self._log(chat, "skipped", "not_idle")
            return True
        if self.turn_activity.any_running():
            self._log(chat, "skipped", "turn_running")
            return False
        if pending_proposals(newest):
            self._log(chat, "skipped", "card_waiting")
            return True

        agent = await self.agent_repo.get_by_id(session.agent_id) if session.agent_id else None
        if agent is None:
            self._log(chat, "skipped", "no_agent")
            return True
        try:
            model = await self.model_resolver.for_agent(agent)
        except Exception as exc:
            self._log(chat, "skipped", f"no_model ({exc})")
            return True
        if model not in loaded:
            loaded[model] = await self.residency.loaded_until(model)
        until = loaded[model]
        # Never load the model just to reflect, and never keep it longer than it would have stayed.
        keep_alive = int((until - now).total_seconds()) if until else 0
        if keep_alive <= 0:
            self._log(chat, "skipped", "model_not_loaded")
            return True

        if self.turn_activity.any_running():
            self._log(chat, "skipped", "turn_running")
            return False
        task = asyncio.ensure_future(self.reflect_chat.execute(session, model=model, keep_alive=keep_alive))
        stop_listening = self.turn_activity.on_turn_start(task.cancel)
        try:
            await task
        except asyncio.CancelledError:
            current = asyncio.current_task()
            if current is not None and current.cancelling():
                task.cancel()
                raise
            self._log(chat, "cancelled_by_turn")
            return False
        except Exception as exc:
            logger.error(
                "Reflection sweep | member=%s session=%s outcome=failed error=%s",
                session.user_id, session.id, exc, exc_info=True,
            )
            return True
        finally:
            stop_listening()
        self._log(chat, "reflected")
        return True
