"""The phone's time zone reaches every tool call of a turn, so calendar times can be read as the member's clock time."""

import pytest

from app.domain.entities.llm_message import LLMResponse, LLMResponseChunk
from tests.domain.test_tool_approval import CHECK, DINNER, USER, _decide, _setup

ZONE = "Europe/Madrid"


@pytest.mark.asyncio
async def test_GIVEN_the_phones_zone_WHEN_a_streamed_turn_runs_a_tool_THEN_the_tool_is_given_the_zone():
    use_case, _, _, tool_executor = _setup([[LLMResponseChunk(tool_calls=[CHECK])], [LLMResponseChunk(delta_content="Free.")]])

    [ev async for ev in use_case.execute_stream(session_id="s1", current_user=USER, content="Am I free?", timezone_name=ZONE)]

    assert [call["zone"] for call in tool_executor.executed_calls] == [ZONE]


@pytest.mark.asyncio
async def test_GIVEN_the_phones_zone_WHEN_an_approved_write_runs_THEN_the_tool_is_given_the_zone_of_the_approval():
    use_case, _, _, tool_executor = _setup([[LLMResponseChunk(tool_calls=[DINNER])], [LLMResponseChunk(delta_content="Added.")]])
    [ev async for ev in use_case.execute_stream(session_id="s1", current_user=USER, content="Add dinner")]

    [
        ev
        async for ev in use_case.decide_stream(
            session_id="s1", current_user=USER, tool_call_id="c1", approved=True, timezone_name=ZONE
        )
    ]

    assert [call["zone"] for call in tool_executor.executed_calls] == [ZONE]


@pytest.mark.asyncio
async def test_GIVEN_the_phones_zone_WHEN_a_turn_that_is_not_streamed_runs_a_tool_THEN_the_tool_is_given_the_zone():
    use_case, _, llm_client, tool_executor = _setup([])
    llm_client.responses = [LLMResponse(content="", tool_calls=[CHECK]), LLMResponse(content="Free.")]

    await use_case.execute(session_id="s1", current_user=USER, content="Am I free?", timezone_name=ZONE)

    assert [call["zone"] for call in tool_executor.executed_calls] == [ZONE]


@pytest.mark.asyncio
async def test_GIVEN_no_zone_from_the_phone_WHEN_a_tool_runs_THEN_it_is_given_none():
    use_case, _, _, tool_executor = _setup([[LLMResponseChunk(tool_calls=[CHECK])], [LLMResponseChunk(delta_content="Free.")]])

    [ev async for ev in use_case.execute_stream(session_id="s1", current_user=USER, content="Am I free?")]

    assert [call["zone"] for call in tool_executor.executed_calls] == [None]
