"""Which calendar events a chat touched, read from its saved tool steps (memory spec §7)."""

from datetime import datetime, timezone

from app.domain.entities.memory import AgentMemory
from app.domain.entities.session import ChatMessage
from app.domain.use_cases.memories.event_links import RemovedEvent, carry_event, removed_events, touched_event_ids


def _answer(*steps):
    return ChatMessage(role="assistant", content="Done.", metadata_json={"tools_executed": list(steps)})


def _step(action, event_id, success=True, **arguments):
    return {
        "tool": "calendar_write",
        "success": success,
        "arguments": dict(arguments),
        "data": {"action": action, "event_id": event_id} if success else None,
    }


def test_GIVEN_events_created_and_changed_THEN_each_is_named_once_in_the_order_first_touched():
    messages = [
        ChatMessage(role="user", content="Add dinner and the dentist"),
        _answer(_step("created", "dinner"), _step("created", "dentist")),
        _answer(_step("updated", "dinner")),
    ]
    assert touched_event_ids(messages) == ["dinner", "dentist"]


def test_GIVEN_an_event_created_then_removed_in_the_chat_THEN_it_is_not_named():
    messages = [_answer(_step("created", "dinner")), _answer(_step("deleted", "dinner"))]
    assert touched_event_ids(messages) == []


def test_GIVEN_one_date_of_a_repeating_event_removed_THEN_the_series_is_still_named():
    messages = [
        _answer(_step("created", "yoga")),
        _answer(_step("deleted", "yoga", occurrence_start="2026-10-21T18:00:00")),
    ]
    assert touched_event_ids(messages) == ["yoga"]


def test_failed_steps_other_tools_and_cards_waiting_are_ignored():
    messages = [
        _answer(
            _step("created", "x", success=False),
            {"tool": "calendar_write", "status": "proposal_pending", "arguments": {"action": "create"}},
            {"tool": "searxng_search", "success": True, "data": {"event_id": "nope"}},
        ),
        ChatMessage(role="assistant", content="No steps", metadata_json=None),
    ]
    assert touched_event_ids(messages) == []


def test_GIVEN_an_event_created_then_removed_in_the_chat_THEN_it_is_listed_as_removed_with_its_title_and_start():
    messages = [
        _answer(_step("created", "dinner", title="Dinner", start_time="2026-10-24T20:00:00")),
        _answer(_step("deleted", "dinner")),
    ]
    assert removed_events(messages, gone_ids=[]) == [RemovedEvent(title="Dinner", start="2026-10-24T20:00:00")]


def test_GIVEN_an_event_gone_from_the_calendar_THEN_it_is_listed_with_its_latest_title_and_start():
    messages = [
        _answer(_step("created", "dinner", title="Dinner", start_time="2026-10-24T14:00:00")),
        _answer(_step("updated", "dinner", title="Dinner together", start_time="2026-10-25T20:00:00")),
    ]
    assert removed_events(messages, gone_ids=["dinner"]) == [
        RemovedEvent(title="Dinner together", start="2026-10-25T20:00:00")
    ]


def test_GIVEN_an_all_day_event_removed_THEN_it_is_listed_as_all_day():
    messages = [
        _answer(_step("created", "trip", title="School trip", start_time="2026-10-23", is_all_day=True)),
        _answer(_step("deleted", "trip")),
    ]
    assert removed_events(messages, gone_ids=[]) == [RemovedEvent(title="School trip", start="2026-10-23", all_day=True)]


def test_GIVEN_one_date_of_a_series_removed_THEN_nothing_is_listed_as_removed():
    messages = [
        _answer(_step("created", "yoga", title="Yoga", start_time="2026-10-21T18:00:00")),
        _answer(_step("deleted", "yoga", occurrence_start="2026-10-21T18:00:00")),
    ]
    assert removed_events(messages, gone_ids=[]) == []


def test_GIVEN_a_gone_event_the_chat_never_named_THEN_it_is_left_out():
    messages = [_answer(_step("deleted", "dinner"))]
    assert removed_events(messages, gone_ids=["dinner", "other"]) == []


_STARTS = datetime(2026, 10, 25, 18, 30, tzinfo=timezone.utc)
_ENDS = datetime(2026, 10, 25, 20, 30, tzinfo=timezone.utc)


def _linked():
    return AgentMemory(
        user_id="emma", content="Dinner together", event_id="ev-dinner", event_starts_at=_STARTS,
        expires_at=_ENDS, time_from_event="timed", moved_from=datetime(2026, 10, 24, 18, 0, tzinfo=timezone.utc),
    )


def test_GIVEN_a_memory_replacing_a_linked_one_WHEN_carried_with_the_link_THEN_it_keeps_the_event_its_start_and_expiry():
    new = AgentMemory(user_id="emma", content="Dinner together is at 21:00")

    carry_event(new, _linked(), link=True)

    assert (new.event_id, new.event_starts_at, new.expires_at) == ("ev-dinner", _STARTS, _ENDS)


def test_GIVEN_a_memory_kept_beside_a_linked_one_WHEN_carried_without_the_link_THEN_it_keeps_only_the_expiry():
    new = AgentMemory(user_id="emma", content="Dinner together is at 21:00")

    carry_event(new, _linked(), link=False)

    assert (new.event_id, new.event_starts_at, new.expires_at) == (None, None, _ENDS)


def test_GIVEN_a_memory_with_its_own_expiry_WHEN_carried_THEN_its_own_expiry_is_kept():
    own = datetime(2026, 10, 26, tzinfo=timezone.utc)
    new = AgentMemory(user_id="emma", content="Dinner together is at 21:00", expires_at=own)

    carry_event(new, _linked(), link=True)

    assert new.expires_at == own


def test_GIVEN_a_rebuilt_linked_memory_WHEN_carried_THEN_the_new_words_stay_as_said():
    new = AgentMemory(user_id="emma", content="Dinner together is at 21:00")

    carry_event(new, _linked(), link=True)

    assert (new.content, new.time_from_event, new.moved_from) == ("Dinner together is at 21:00", None, None)


def test_GIVEN_a_memory_with_no_event_WHEN_carried_THEN_nothing_changes():
    new = AgentMemory(user_id="emma", content="Sam is vegan now.")

    carry_event(new, AgentMemory(user_id="emma", content="Sam is vegetarian."), link=True)

    assert (new.event_id, new.event_starts_at, new.expires_at) == (None, None, None)
