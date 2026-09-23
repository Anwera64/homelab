from datetime import datetime, timezone
from app.domain.entities.user import User
from app.domain.entities.space import Space
from app.domain.entities.agent import AgentPersonality
from app.domain.entities.session import ConversationSession, ChatMessage
from app.domain.entities.memory import AgentMemory

from app.presentation.mappers.user_presentation_mapper import UserPresentationMapper
from app.presentation.mappers.space_presentation_mapper import SpacePresentationMapper
from app.presentation.mappers.agent_presentation_mapper import AgentPresentationMapper
from app.presentation.mappers.session_presentation_mapper import SessionPresentationMapper
from app.presentation.mappers.memory_presentation_mapper import MemoryPresentationMapper


def test_user_presentation_mapper():
    now = datetime.now(timezone.utc)
    user = User(
        id="u-1",
        full_name="Anwera",
        hashed_pin="pin-hash",
        avatar_color="#C05638",
        is_admin=True,
        is_active=True,
        personal_space_id="space-1",
        created_at=now,
    )

    resp = UserPresentationMapper.to_response(user)
    assert resp.id == "u-1"
    assert resp.full_name == "Anwera"
    assert "hashed_pin" not in resp.model_dump()
    assert resp.is_admin is True
    assert resp.personal_space_id == "space-1"


def test_space_presentation_mapper():
    now = datetime.now(timezone.utc)
    space = Space(
        id="s-1",
        name="Household Hub",
        type="shared",
        owner_id=None,
        settings={"columns": 4},
        created_at=now,
        updated_at=now,
    )
    resp = SpacePresentationMapper.to_response(space)
    assert resp.id == "s-1"
    assert resp.type == "shared"
    assert resp.settings["columns"] == 4


def test_agent_presentation_mapper():
    now = datetime.now(timezone.utc)
    agent = AgentPersonality(
        id="a-1",
        slug="assistant",
        name="Assistant",
        description="Helper",
        avatar="🏡",
        system_prompt="Help",
        llm_model_id="m-1",
        created_at=now,
    )
    resp = AgentPresentationMapper.to_response(agent)
    assert resp.slug == "assistant"
    assert resp.llm_model_id == "m-1"
    assert not hasattr(resp, "model_alias")


def test_session_and_message_presentation_mapper():
    now = datetime.now(timezone.utc)
    session = ConversationSession(
        id="sess-1",
        user_id="u-1",
        agent_id="a-1",
        title="Chat",
        is_secret=True,
        is_archived=False,
        created_at=now,
        updated_at=now,
    )
    msg = ChatMessage(
        id="m-1",
        session_id="sess-1",
        role="user",
        content="Hi",
        metadata_json={},
        created_at=now,
    )
    resp = SessionPresentationMapper.to_detail_response(session, [msg])
    assert resp.id == "sess-1"
    assert resp.is_secret is True
    assert len(resp.messages) == 1
    assert resp.messages[0].content == "Hi"


def test_memory_presentation_mapper():
    now = datetime.now(timezone.utc)
    mem = AgentMemory(
        id="mem-1",
        user_id="u-1",
        agent_id="a-1",
        scope="personal",
        category="fact",
        content="Likes tea",
        confidence=1.0,
        created_at=now,
        updated_at=now,
    )
    resp = MemoryPresentationMapper.to_response(mem)
    assert resp.id == "mem-1"
    assert resp.content == "Likes tea"
