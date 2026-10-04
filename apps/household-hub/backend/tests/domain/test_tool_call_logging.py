"""Every tool step says in the hub's log what it was asked, and why it failed if it did, so it can be read back afterwards."""

import logging
from unittest.mock import MagicMock

import pytest

from app.domain.entities.agent import AgentPersonality
from app.domain.entities.llm_message import LLMToolCall
from app.domain.entities.tool_definition import ToolExecutionResult
from app.domain.exceptions import ToolPermissionDeniedException
from app.domain.use_cases.chat.process_chat_turn import ProcessChatTurnUseCase

LOGGER = "app.domain.use_cases.chat.process_chat_turn"
REMOVE_GYM = LLMToolCall(
    id="call-1",
    name="calendar_write",
    arguments={"action": "delete", "event_id": "gym-1", "occurrence_start": "2026-10-12T08:00:00+02:00"},
)


class _Executor:
    def __init__(self, result=None, raises=None):
        self.result, self.raises = result, raises

    async def execute(self, tool_name, arguments, user_id, agent_tool_permissions, is_secret_mode=False, sources=None):
        if self.raises:
            raise self.raises
        return self.result


def _turns(executor) -> ProcessChatTurnUseCase:
    return ProcessChatTurnUseCase(
        session_repo=MagicMock(),
        agent_repo=MagicMock(),
        llm_client=MagicMock(),
        context_assembler=MagicMock(),
        tool_executor=executor,
        tool_lister=MagicMock(),
        model_resolver=MagicMock(),
        uow=MagicMock(),
    )


async def _run(executor, call=REMOVE_GYM, secret=False):
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_write"])
    return await _turns(executor)._run_tool(call, agent, "u1", secret, sources=None, offered=[])


def _failed(error="The calendar still has event 'gym-1' after removing it.", reason=None):
    return ToolExecutionResult(tool_name="calendar_write", success=False, error=error, reason=reason)


@pytest.mark.asyncio
async def test_GIVEN_a_tool_step_fails_WHEN_it_runs_THEN_the_log_says_which_tool_why_and_what_it_was_asked(caplog):
    with caplog.at_level(logging.WARNING, logger=LOGGER):
        await _run(_Executor(_failed()))

    (line,) = [record.getMessage() for record in caplog.records]
    assert "calendar_write" in line
    assert "The calendar still has event 'gym-1' after removing it." in line
    assert "2026-10-12T08:00:00+02:00" in line and "gym-1" in line


@pytest.mark.asyncio
async def test_GIVEN_a_failure_with_a_reason_code_WHEN_it_runs_THEN_the_log_carries_the_code(caplog):
    with caplog.at_level(logging.WARNING, logger=LOGGER):
        await _run(_Executor(_failed(error="No calendar configured for user.", reason="calendar_not_connected")))

    assert "calendar_not_connected" in caplog.records[0].getMessage()


@pytest.mark.asyncio
async def test_GIVEN_a_secret_turn_WHEN_a_tool_step_fails_THEN_the_log_leaves_out_what_it_was_asked(caplog):
    with caplog.at_level(logging.WARNING, logger=LOGGER):
        await _run(_Executor(_failed(error="No calendar configured for user.")), secret=True)

    (line,) = [record.getMessage() for record in caplog.records]
    assert "calendar_write" in line and "No calendar configured for user." in line
    assert "gym-1" not in line and "2026-10-12" not in line


@pytest.mark.asyncio
async def test_GIVEN_long_arguments_WHEN_a_tool_step_fails_THEN_the_log_keeps_only_their_start(caplog):
    note = LLMToolCall(id="call-2", name="document_writer", arguments={"title": "Diary", "content": "x" * 5000})

    with caplog.at_level(logging.WARNING, logger=LOGGER):
        await _run(_Executor(_failed(error="Could not save the note.")), call=note)

    assert len(caplog.records[0].getMessage()) < 1000


@pytest.mark.asyncio
async def test_GIVEN_a_tool_the_agent_does_not_have_WHEN_it_is_called_THEN_that_failure_is_logged_too(caplog):
    with caplog.at_level(logging.WARNING, logger=LOGGER):
        result = await _run(_Executor(raises=ToolPermissionDeniedException("not allowed")))

    assert result.success is False
    assert "There is no tool 'calendar_write'" in caplog.records[0].getMessage()


DONE = ToolExecutionResult(tool_name="calendar_write", success=True, data={"action": "deleted"})


@pytest.mark.asyncio
async def test_GIVEN_a_tool_step_succeeds_WHEN_it_runs_THEN_the_log_says_which_tool_and_what_it_was_asked(caplog):
    with caplog.at_level(logging.INFO, logger=LOGGER):
        await _run(_Executor(DONE))

    (record,) = caplog.records
    assert record.levelno == logging.INFO
    assert "calendar_write ok" in record.getMessage()
    assert "2026-10-12T08:00:00+02:00" in record.getMessage() and "gym-1" in record.getMessage()
    assert "deleted" not in record.getMessage(), "what a tool gave back is not logged, only what it was asked"


@pytest.mark.asyncio
async def test_GIVEN_a_secret_turn_WHEN_a_tool_step_succeeds_THEN_the_log_leaves_out_what_it_was_asked(caplog):
    with caplog.at_level(logging.INFO, logger=LOGGER):
        await _run(_Executor(DONE), secret=True)

    (line,) = [record.getMessage() for record in caplog.records]
    assert "calendar_write ok" in line
    assert "gym-1" not in line and "2026-10-12" not in line


@pytest.mark.asyncio
async def test_GIVEN_long_arguments_WHEN_a_tool_step_succeeds_THEN_the_log_keeps_only_their_start(caplog):
    note = LLMToolCall(id="call-2", name="document_writer", arguments={"title": "Diary", "content": "x" * 5000})

    with caplog.at_level(logging.INFO, logger=LOGGER):
        await _run(_Executor(DONE), call=note)

    assert len(caplog.records[0].getMessage()) < 1000