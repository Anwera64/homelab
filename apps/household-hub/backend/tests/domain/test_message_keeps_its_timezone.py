"""
Reflection runs long after a turn, with no phone to ask, so the member's message keeps the zone its
phone sent: "tomorrow" is then resolved to the day it meant there (memory spec §2.2).
"""

import pytest

from app.domain.entities.llm_message import LLMResponse
from app.domain.entities.session import ConversationSession
from app.domain.entities.user import User
from tests.domain.test_chat_title import _use_case
from tests.domain.test_chat_turn import FakeLLMClient, FakeSessionRepository


@pytest.mark.asyncio
async def test_GIVEN_the_phones_zone_WHEN_a_message_is_sent_THEN_the_message_keeps_it():
    repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])

    await _use_case(repo, FakeLLMClient([LLMResponse(content="Ok")])).execute(
        session_id="s1", current_user=User(id="u1"), content="Dentist tomorrow", timezone_name="Europe/Madrid"
    )
    async for _ in _use_case(repo, FakeLLMClient()).execute_stream(
        session_id="s1", current_user=User(id="u1"), content="And Friday", timezone_name="Europe/Madrid"
    ):
        pass

    sent = [m for m in repo.messages if m.role == "user"]
    assert [m.metadata_json.get("timezone") for m in sent] == ["Europe/Madrid", "Europe/Madrid"]


@pytest.mark.asyncio
async def test_GIVEN_no_zone_WHEN_a_message_is_sent_THEN_none_is_kept():
    repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])

    await _use_case(repo, FakeLLMClient([LLMResponse(content="Ok")])).execute(
        session_id="s1", current_user=User(id="u1"), content="Hi"
    )

    assert "timezone" not in repo.messages[0].metadata_json
