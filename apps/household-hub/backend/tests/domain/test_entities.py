from datetime import datetime, timezone, timedelta
import pytest

from app.domain.entities.user import User
from app.domain.entities.space import Space
from app.domain.entities.agent import AgentPersonality
from app.domain.entities.session import ConversationSession, ChatMessage
from app.domain.entities.memory import AgentMemory
from app.domain.exceptions import (
    DomainException,
    EntityNotFoundException,
    ZeroLeakViolationException,
    SoleAdminDeletionException,
    SlugConflictException,
    TrashGracePeriodException,
    SecretModeViolationException,
    InvalidOperationException,
)


def test_user_entity_defaults():
    user = User(
        id="user-1",
        username="anwera",
        email="anwera@homelab.local",
        full_name="Anwera",
        hashed_password="hashed_pw",
        is_admin=False,
    )
    assert user.id == "user-1"
    assert user.is_active is True
    assert user.avatar_color == "#4F46E5"


def test_space_entity_zero_leak_rules():
    personal_space = Space(
        id="space-1",
        name="Anwera's Space",
        type="personal",
        owner_id="user-1",
    )
    shared_space = Space(
        id="space-shared",
        name="Household Hub",
        type="shared",
        owner_id=None,
    )

    # Personal space: only owner can access
    assert personal_space.can_access(user_id="user-1") is True
    assert personal_space.can_access(user_id="user-admin") is False

    # Shared space: anyone can access
    assert shared_space.can_access(user_id="user-1") is True
    assert shared_space.can_access(user_id="user-admin") is True


def test_agent_personality_trash_and_grace_period():
    now = datetime.now(timezone.utc)
    agent = AgentPersonality(
        id="agent-1",
        slug="researcher",
        name="Researcher",
        system_prompt="Research things",
        owner_id="user-1",
        is_builtin=False,
        deleted_at=now - timedelta(days=2),
    )

    assert agent.is_in_trash is True
    assert agent.days_remaining_in_grace_period(now=now, grace_days=7) == 5
    assert agent.can_restore(now=now, grace_days=7) is True

    # Expired agent (> 7 days)
    expired_agent = AgentPersonality(
        id="agent-2",
        slug="old-agent",
        name="Old Agent",
        system_prompt="Old prompt",
        owner_id="user-1",
        deleted_at=now - timedelta(days=8),
    )
    assert expired_agent.days_remaining_in_grace_period(now=now, grace_days=7) == 0
    assert expired_agent.can_restore(now=now, grace_days=7) is False


def test_session_and_message_entity():
    session = ConversationSession(
        id="sess-1",
        user_id="user-1",
        agent_id="agent-1",
        title="Chat",
        is_secret=True,
    )
    assert session.is_secret is True
    assert session.is_archived is False

    msg = ChatMessage(
        id="msg-1",
        session_id="sess-1",
        role="user",
        content="Hello agent",
    )
    assert msg.role == "user"
    assert msg.content == "Hello agent"


def test_agent_memory_validation():
    mem = AgentMemory(
        id="mem-1",
        user_id="user-1",
        agent_id="agent-1",
        scope="personal",
        category="preference",
        content="Prefers dark mode",
        confidence=0.95,
    )
    assert mem.scope == "personal"
    assert mem.confidence == 0.95
