"""The hub looks up the event a remove or change card is about on the member's real calendar (#63)."""
from unittest.mock import MagicMock

from app.bootstrap.di import _caldav_connector, get_container
from app.domain.use_cases.integrations.get_calendar_event import GetCalendarEventUseCase
from app.presentation.api import deps as pres_deps


def test_the_chat_turn_looks_events_up_on_the_calendar_connector():
    turn = get_container(MagicMock())[pres_deps.get_process_chat_turn_use_case]

    assert isinstance(turn.event_lookup, GetCalendarEventUseCase)
    assert turn.event_lookup.connector is _caldav_connector


def test_reflection_reads_the_events_a_chat_touched_from_the_calendar_connector():
    """Memory spec §7: a fact is linked to an event only once the calendar confirms it is still there."""
    from app.domain.use_cases.memories.reflection_sweep import ReflectionSweepUseCase

    reflect = get_container(MagicMock())[ReflectionSweepUseCase].reflect_chat

    assert isinstance(reflect.event_lookup, GetCalendarEventUseCase)
    assert reflect.event_lookup.connector is _caldav_connector
