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


def test_every_hub_route_that_changes_an_event_tells_its_memories():
    """Memory spec §7: the agent's calendar_write and the phone's edit routes both go through the follow."""
    from app.domain.use_cases.memories.follow_event import CheckEventMemoriesUseCase, FollowEventChangeUseCase

    container = get_container(MagicMock())
    turn = container[pres_deps.get_process_chat_turn_use_case]
    routes = [
        container[pres_deps.get_update_calendar_event_use_case],
        container[pres_deps.get_delete_calendar_event_use_case],
        turn.tool_executor.update_calendar_event_uc,
        turn.tool_executor.delete_calendar_event_uc,
        container[pres_deps.get_execute_tool_use_case].update_calendar_event_uc,
        container[pres_deps.get_execute_tool_use_case].delete_calendar_event_uc,
    ]

    for route in routes:
        assert isinstance(route.follow, FollowEventChangeUseCase)
        assert route.follow.connector is _caldav_connector
    check = container[CheckEventMemoriesUseCase]
    assert isinstance(check.follow, FollowEventChangeUseCase)
