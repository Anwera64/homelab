import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone
from itertools import groupby
from typing import Callable, Dict, List, Optional, Tuple

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.memory import ALL_DAY, TIMED, AgentMemory
from app.domain.entities.memory_proposal import FROM_REFLECTION
from app.domain.exceptions import (
    CalendarIntegrationException,
    CalendarNotConnectedException,
    CalendarNotPickedException,
)
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.memory_proposal_repository import IMemoryProposalRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver
from app.domain.use_cases.integrations.calendar_sources import SyncCalendarSourcesUseCase, on
from app.domain.use_cases.memories.event_links import event_expiry

logger = logging.getLogger(__name__)

RETIRED = "retired"
MOVED = "moved"
RENAMED = "renamed"
SERIES_CHANGED = "series_changed"


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _aware(value: Optional[datetime]) -> Optional[datetime]:
    # SQLite gives datetimes back without their zone; every one the hub writes is UTC.
    return value.replace(tzinfo=timezone.utc) if value is not None and value.tzinfo is None else value


def match_event(memory: AgentMemory, event: Optional[CalendarEvent], now: datetime) -> Optional[str]:
    """
    Makes [memory] true to its [event] as the calendar has it now (memory spec §7), and says what
    changed, or None. Gone retires it. A one-off event that moved becomes its title, its new start and
    the start it had, and every reader adds the time in their own zone; no model is asked and no change
    is raised to check, since the member made the change. A repeating series keeps its sentence: only
    when it ends follows.
    """
    if event is None:
        memory.is_active = False
        memory.retired_at = now
        return RETIRED
    if event.repeat is not None:
        expires = event_expiry(event)
        if _aware(memory.expires_at) == _aware(expires):
            return None
        memory.expires_at = expires
        return SERIES_CHANGED

    start, kind = _aware(event.start_time), ALL_DAY if event.is_all_day else TIMED
    title = " ".join(event.title.split())
    if _aware(memory.event_starts_at) != start:
        memory.moved_from = _aware(memory.event_starts_at)
        memory.event_starts_at = start
        memory.expires_at = event.end_time
        memory.content = title
        memory.time_from_event = kind
        return MOVED
    if memory.time_from_event and memory.content != title:
        memory.content = title
        memory.time_from_event = kind
        return RENAMED
    if _aware(memory.expires_at) != _aware(event.end_time):
        # It ends at another time, but still starts when the sentence says: nothing to show.
        memory.expires_at = event.end_time
    return None


class FollowEventChangeUseCase:
    """
    Keeps a member's memories of one event true after the hub changed or removed it (memory spec §7).
    Runs after the calendar answered, and reads the event back rather than trusting the call: a date
    of a series, a split series and a removal all come out right. A calendar that can't be read now
    changes nothing; the periodic check tries again.
    """

    def __init__(
        self,
        memory_repo: IMemoryRepository,
        proposal_repo: IMemoryProposalRepository,
        uow: IUnitOfWork,
        credential_repo: ICalendarCredentialRepository,
        connector: ICalendarConnector,
        secrets: CalendarSecretResolver,
        clock: Callable[[], datetime] = _utc_now,
        sync: Optional[SyncCalendarSourcesUseCase] = None,
    ):
        self.memory_repo = memory_repo
        self.proposal_repo = proposal_repo
        self.uow = uow
        self.credential_repo = credential_repo
        self.connector = connector
        self.secrets = secrets
        self.clock = clock
        # Without it, the connection's one calendar is read, as before calendars could be picked.
        self.sync = sync

    async def execute(self, user_id: str, event_id: str, calendar: Optional[str] = None) -> None:
        memories = await self.memory_repo.list_by_event(user_id, event_id)
        if not memories:
            return
        calendar = calendar or next((m.event_calendar for m in memories if m.event_calendar), None)
        try:
            event = await self.read_event(user_id, event_id, calendar)
        except CalendarNotPickedException:
            logger.info("Calendar link | member=%s event=%s is on a calendar not read, left alone", user_id, event_id)
            return
        except CalendarIntegrationException as exc:
            logger.warning("Calendar link | member=%s event=%s could not be read, left for the check: %s", user_id, event_id, exc)
            return
        outcomes = await self.save(memories, event)
        for memory_id, outcome in outcomes.items():
            logger.info("Calendar link | member=%s memory=%s event=%s outcome=%s", user_id, memory_id, event_id, outcome)

    async def read_event(
        self, user_id: str, event_id: str, calendar: Optional[str] = None, timeout: float = 10.0
    ) -> Optional[CalendarEvent]:
        """
        The event as the member's calendar has it, or None when it has none. Raises when it can't say,
        and when [calendar] is one the member doesn't read: an event there isn't gone. A link made
        before calendars could be picked names none, so each picked calendar is looked through.
        """
        credential = await self.credential_repo.get_by_user_id(user_id)
        if not credential or not credential.is_active:
            # No calendar is not the same as no event: nothing is retired for it.
            raise CalendarNotConnectedException("No calendar configured for user.")
        secret = await self.secrets.resolve(credential)
        if self.sync is None:
            return await self.connector.get_event(
                credential=credential, secret=secret, event_id=event_id, timeout=timeout
            )
        picked = [s for s in await self.sync.execute(credential, secret) if s.picked]
        if calendar:
            picked = [s for s in picked if s.key == calendar]
            if not picked:
                raise CalendarNotPickedException(f"Calendar '{calendar}' is not read.")
        for source in picked:
            event = await self.connector.get_event(
                credential=credential, secret=secret, event_id=event_id, timeout=timeout, calendar_url=source.url
            )
            if event is not None:
                return on(event, source)
        return None

    async def save(self, memories: List[AgentMemory], event: Optional[CalendarEvent]) -> Dict[str, str]:
        """Matches each memory to [event] and saves those that changed, in one commit."""
        now = self.clock()
        outcomes: Dict[str, str] = {}
        # Links that named no calendar now name the one the event was found on.
        filled = set()
        for memory in memories:
            if event is not None and event.calendar_key and memory.event_calendar != event.calendar_key:
                memory.event_calendar = event.calendar_key
                filled.add(memory.id)
            outcome = match_event(memory, event, now)
            if outcome:
                outcomes[memory.id] = outcome
        if not outcomes and not filled:
            return outcomes
        async with self.uow:
            for memory in memories:
                if memory.id not in outcomes and memory.id not in filled:
                    continue
                memory.updated_at = now
                await self.memory_repo.update(memory)
                if outcomes.get(memory.id) == RETIRED:
                    # Reflection's changes to it have nothing left to change. A member's own
                    # `remember` is left for them to answer.
                    for dropped in await self.proposal_repo.drop_pending_for(memory.id, origin=FROM_REFLECTION):
                        logger.info(
                            "Memory proposal dropped | memory=%s proposal=%s reason=event_removed", memory.id, dropped
                        )
            await self.uow.commit()
        return outcomes


@dataclass
class EventCheckResult:
    checked: int = 0
    changed: int = 0
    retired: int = 0
    unreachable: List[str] = field(default_factory=list)


class CheckEventMemoriesUseCase:
    """
    The periodic check (memory spec §7): events changed outside the hub, in a calendar app. The hub
    keeps no copy of the calendar, so each linked, unexpired memory's event is read live. A member whose
    calendar isn't connected, refuses the sign-in or can't be reached is skipped, and nothing of theirs
    changes until it answers again.
    """

    def __init__(
        self,
        memory_repo: IMemoryRepository,
        follow: FollowEventChangeUseCase,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.memory_repo = memory_repo
        self.follow = follow
        self.clock = clock

    async def run_pass(self) -> EventCheckResult:
        result = EventCheckResult()
        linked = await self.memory_repo.list_linked_current(self.clock())
        for user_id, theirs in groupby(sorted(linked, key=lambda m: m.user_id), key=lambda m: m.user_id):
            await self._check_member(user_id, list(theirs), result)
        logger.info(
            "Event check | checked=%d changed=%d retired=%d unreachable=%s",
            result.checked, result.changed, result.retired, ",".join(result.unreachable),
        )
        return result

    async def _check_member(self, user_id: str, memories: List[AgentMemory], result: EventCheckResult) -> None:
        by_event: Dict[Tuple[str, Optional[str]], List[AgentMemory]] = {}
        for memory in memories:
            by_event.setdefault((memory.event_id, memory.event_calendar), []).append(memory)
        events: Dict[Tuple[str, Optional[str]], Optional[CalendarEvent]] = {}
        try:
            for event_id, calendar in list(by_event):
                try:
                    events[(event_id, calendar)] = await self.follow.read_event(user_id, event_id, calendar)
                except CalendarNotPickedException:
                    # A calendar the member turned off: its events aren't gone, and wait unread.
                    del by_event[(event_id, calendar)]
        except CalendarIntegrationException as exc:
            # One answer missing would retire memories whose events are fine: the whole member waits.
            result.unreachable.append(user_id)
            logger.warning("Event check | member=%s calendar could not be read, nothing changed: %s", user_id, exc)
            return
        for (event_id, calendar), linked in by_event.items():
            result.checked += len(linked)
            outcomes = await self.follow.save(linked, events[(event_id, calendar)])
            for memory_id, outcome in outcomes.items():
                if outcome == RETIRED:
                    result.retired += 1
                else:
                    result.changed += 1
                logger.info("Calendar link | member=%s memory=%s event=%s outcome=%s from=check", user_id, memory_id, event_id, outcome)
