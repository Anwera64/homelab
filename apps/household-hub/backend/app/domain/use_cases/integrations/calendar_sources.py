from dataclasses import replace
from typing import List, Optional

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.calendar_source import CalendarSource, DiscoveredCalendar
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.exceptions import CalendarChoiceException, CalendarNotConnectedException
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_source_repository import ICalendarSourceRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver

KEY_PREFIX = "c"

# What a calendar is chosen for: a new event, a change or removal of one, or only looking at it.
CREATE = "create"
CHANGE = "change"
LOOK = "look"


def _number(key: str) -> int:
    digits = key[len(KEY_PREFIX):]
    return int(digits) if digits.isdigit() else 0


def merge(stored: List[CalendarSource], account: List[DiscoveredCalendar]) -> List[CalendarSource]:
    """
    The account's calendars as the hub keeps them. A calendar already known keeps its key and the
    member's choice; a new one is read when the member can write to it, since a read-only one is
    mostly a subscription such as holidays. A key is never handed out twice.
    """
    known = {s.url: s for s in stored}
    next_number = max((_number(s.key) for s in stored), default=0) + 1
    merged: List[CalendarSource] = []
    for calendar in account:
        before = known.get(calendar.url)
        if before is not None:
            merged.append(
                CalendarSource(
                    key=before.key, url=calendar.url, name=calendar.name, writable=calendar.writable, picked=before.picked
                )
            )
            continue
        merged.append(
            CalendarSource(
                key=f"{KEY_PREFIX}{next_number}",
                url=calendar.url,
                name=calendar.name,
                writable=calendar.writable,
                picked=calendar.writable,
            )
        )
        next_number += 1
    return merged


class SyncCalendarSourcesUseCase:
    """
    Brings the member's kept calendars in line with what their account has now. It runs before every
    calendar step, so it writes only when the account changed, and commits then: a read has no
    commit of its own to ride on, and the keys handed to the model must outlive it.
    """

    def __init__(
        self,
        source_repo: ICalendarSourceRepository,
        connector: ICalendarConnector,
        uow: Optional[IUnitOfWork] = None,
    ):
        self.source_repo = source_repo
        self.connector = connector
        self.uow = uow

    async def execute(self, credential: CalendarCredential, secret: str) -> List[CalendarSource]:
        account = await self.connector.list_calendars(credential, secret)
        stored = await self.source_repo.list_for(credential.id)
        synced = merge(stored, account)
        if synced != stored:
            await self.save(credential, synced)
        return synced

    async def save(self, credential: CalendarCredential, sources: List[CalendarSource]) -> None:
        if self.uow is None:
            await self.source_repo.replace_all(credential.id, sources)
            return
        async with self.uow:
            await self.source_repo.replace_all(credential.id, sources)
            await self.uow.commit()

    async def choose(
        self, credential: CalendarCredential, secret: str, key: Optional[str], purpose: str
    ) -> CalendarSource:
        return choose(await self.execute(credential, secret), key, purpose)


class PickCalendarsUseCase:
    """The member says which of their calendars the hub reads; every other one is turned off."""

    def __init__(self, sync: SyncCalendarSourcesUseCase, source_repo: ICalendarSourceRepository, uow: IUnitOfWork):
        self.sync = sync
        self.source_repo = source_repo
        self.uow = uow

    async def execute(self, credential: CalendarCredential, secret: str, keys: List[str]) -> List[CalendarSource]:
        sources = await self.sync.execute(credential, secret)
        unknown = sorted(set(keys) - {s.key for s in sources})
        if unknown:
            raise CalendarChoiceException(f"The account has no calendar {', '.join(unknown)}.")
        picked = [replace(s, picked=s.key in keys) for s in sources]
        async with self.uow:
            await self.source_repo.replace_all(credential.id, picked)
            await self.uow.commit()
        return picked


class MemberCalendarsUseCase:
    """The calendars of a member's account as the phone shows them, and the member's pick of them."""

    def __init__(
        self,
        credential_repo: ICalendarCredentialRepository,
        secrets: CalendarSecretResolver,
        sync: SyncCalendarSourcesUseCase,
        pick: PickCalendarsUseCase,
    ):
        self.credential_repo = credential_repo
        self.secrets = secrets
        self.sync = sync
        self.pick_uc = pick

    async def list(self, user_id: str) -> List[CalendarSource]:
        credential, secret = await self._connection(user_id)
        return await self.sync.execute(credential, secret)

    async def pick(self, user_id: str, keys: List[str]) -> List[CalendarSource]:
        credential, secret = await self._connection(user_id)
        return await self.pick_uc.execute(credential, secret, keys)

    async def _connection(self, user_id: str):
        credential = await self.credential_repo.get_by_user_id(user_id)
        if not credential or not credential.is_active:
            raise CalendarNotConnectedException("No calendar configured for user.")
        return credential, await self.secrets.resolve(credential)


class CalendarForCardUseCase:
    """
    The name of the calendar a new event goes to, for its card: the one named, or the only one the
    member can write to. None without a kept calendar to say; refuses as the write would.
    """

    def __init__(
        self,
        credential_repo: ICalendarCredentialRepository,
        secrets: CalendarSecretResolver,
        sync: SyncCalendarSourcesUseCase,
    ):
        self.credential_repo = credential_repo
        self.secrets = secrets
        self.sync = sync

    async def execute(self, user_id: str, key: Optional[str]) -> Optional[str]:
        credential = await self.credential_repo.get_by_user_id(user_id)
        if not credential or not credential.is_active:
            return None
        secret = await self.secrets.resolve(credential)
        return (await self.sync.choose(credential, secret, key, CREATE)).name


def _named(sources: List[CalendarSource]) -> str:
    return ", ".join(f"{s.name} ({s.key})" for s in sources) or "none"


def choose(sources: List[CalendarSource], key: Optional[str], purpose: str) -> CalendarSource:
    """
    The calendar a step is for. A new event with more than one calendar to write to needs the
    member's word on which: the model is told to ask, not to guess. A change names the calendar
    calendar_read gave, since the same event id can't be looked for everywhere.
    """
    picked = [s for s in sources if s.picked]
    writable = [s for s in picked if s.writable]
    if key:
        source = next((s for s in picked if s.key == key), None)
        if source is None:
            raise CalendarChoiceException(
                f"The member has no calendar '{key}' to use. Their calendars: {_named(picked)}."
            )
        if purpose != LOOK and not source.writable:
            raise CalendarChoiceException(
                f"{source.name} ({source.key}) is read-only: nothing on it can be added, changed or removed."
            )
        return source
    if purpose == CREATE:
        if len(writable) == 1:
            return writable[0]
        if not writable:
            raise CalendarChoiceException("The member has no calendar the hub may add events to.")
        raise CalendarChoiceException(
            f"The member has more than one calendar to add it to: {_named(writable)}. Ask the member "
            "which one, then pass its key as 'calendar'."
        )
    if len(picked) == 1:
        return picked[0]
    raise CalendarChoiceException(
        f"Say which calendar the event is on: pass 'calendar' as calendar_read gave it. "
        f"The member's calendars: {_named(picked)}."
    )


def on(event: Optional[CalendarEvent], source: CalendarSource) -> Optional[CalendarEvent]:
    """[event] as the calendar it is on: its key, its name, and whether it can be changed."""
    if event is None:
        return None
    return replace(event, calendar_key=source.key, calendar_name=source.name, read_only=not source.writable)
