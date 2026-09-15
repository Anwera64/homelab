import pytest
from datetime import datetime, timezone

from app.domain.entities.user import User
from app.domain.entities.space import Space
from app.domain.entities.agent import AgentPersonality
from app.domain.entities.session import ConversationSession
from app.domain.entities.memory import AgentMemory
from app.domain.use_cases.auth.authenticate_token import AuthenticateTokenUseCase
from app.domain.exceptions import (
    AuthenticationException,
    NameTakenException,
    SoleAdminDeletionException,
    InvalidOperationException,
    SecretModeViolationException,
    EntityNotFoundException,
)
from app.domain.use_cases.auth.register_initial_admin import RegisterInitialAdminUseCase
from app.domain.use_cases.users.create_member import CreateMemberUseCase
from app.domain.use_cases.users.delete_member import DeleteMemberUseCase
from app.domain.use_cases.memories.create_memory import CreateMemoryUseCase


class FakeUserRepository:
    def __init__(self, users=None):
        self.users = {u.id: u for u in (users or [])}

    async def count(self) -> int:
        return len(self.users)

    async def get_by_id(self, user_id: str):
        return self.users.get(user_id)

    async def list_all(self):
        return list(self.users.values())

    async def list_active(self):
        return [u for u in self.users.values() if u.is_active]

    async def create(self, user: User) -> User:
        self.users[user.id] = user
        return user

    async def update(self, user: User) -> User:
        self.users[user.id] = user
        return user

    async def delete(self, user_id: str) -> None:
        self.users.pop(user_id, None)

    async def count_admins(self) -> int:
        return sum(1 for u in self.users.values() if u.is_admin)

    async def get_other_admin(self, exclude_user_id: str):
        for u in self.users.values():
            if u.is_admin and u.id != exclude_user_id:
                return u
        return None


class FakeTokenService:
    """Decodes every token to the payload it was given."""

    def __init__(self, payload: dict):
        self.payload = payload

    def decode_token(self, token: str) -> dict:
        return self.payload


@pytest.mark.asyncio
async def test_a_token_of_the_members_current_version_is_accepted():
    emma = User(full_name="Emma", token_version=2)
    use_case = AuthenticateTokenUseCase(FakeUserRepository([emma]), FakeTokenService({"sub": emma.id, "ver": 2}))

    assert await use_case.execute("token") is emma


@pytest.mark.asyncio
async def test_a_token_from_an_older_version_is_refused():
    emma = User(full_name="Emma", token_version=2)
    use_case = AuthenticateTokenUseCase(FakeUserRepository([emma]), FakeTokenService({"sub": emma.id, "ver": 1}))

    with pytest.raises(AuthenticationException):
        await use_case.execute("token")


@pytest.mark.asyncio
async def test_a_token_without_a_version_is_refused():
    emma = User(full_name="Emma")
    use_case = AuthenticateTokenUseCase(FakeUserRepository([emma]), FakeTokenService({"sub": emma.id}))

    with pytest.raises(AuthenticationException):
        await use_case.execute("token")


class FakeSpaceRepository:
    def __init__(self, spaces=None):
        self.spaces = {s.id: s for s in (spaces or [])}

    async def get_shared(self):
        for s in self.spaces.values():
            if s.type == "shared":
                return s
        return None

    async def get_by_owner_id(self, owner_id: str):
        for s in self.spaces.values():
            if s.owner_id == owner_id:
                return s
        return None

    async def get_by_id(self, space_id: str):
        return self.spaces.get(space_id)

    async def create(self, space: Space) -> Space:
        self.spaces[space.id] = space
        return space

    async def update(self, space: Space) -> Space:
        self.spaces[space.id] = space
        return space

    async def delete_by_owner_id(self, owner_id: str) -> None:
        to_del = [s.id for s in self.spaces.values() if s.owner_id == owner_id]
        for sid in to_del:
            self.spaces.pop(sid, None)


class FakeAgentRepository:
    def __init__(self, agents=None):
        self.agents = {a.id: a for a in (agents or [])}

    async def reassign_owner(self, from_user_id: str, to_user_id: str):
        for a in self.agents.values():
            if a.owner_id == from_user_id:
                a.owner_id = to_user_id

    async def get_by_id(self, agent_id: str):
        return self.agents.get(agent_id)


class FakeMemoryRepository:
    def __init__(self, memories=None):
        self.memories = {m.id: m for m in (memories or [])}

    async def delete_personal_memories(self, user_id: str):
        to_del = [m.id for m in self.memories.values() if m.user_id == user_id and m.scope == "personal"]
        for mid in to_del:
            self.memories.pop(mid, None)

    async def reassign_household_memories(self, from_user_id: str, to_user_id: str):
        for m in self.memories.values():
            if m.user_id == from_user_id and m.scope == "household":
                m.user_id = to_user_id

    async def get_by_id(self, memory_id: str):
        return self.memories.get(memory_id)

    async def create(self, memory: AgentMemory) -> AgentMemory:
        self.memories[memory.id] = memory
        return memory


class FakeSessionRepository:
    def __init__(self, sessions=None):
        self.sessions = {s.id: s for s in (sessions or [])}

    async def get_by_id(self, session_id: str):
        return self.sessions.get(session_id)


class FakePasswordHasher:
    def hash(self, password: str) -> str:
        return f"hashed_{password}"

    def verify(self, plain: str, hashed: str) -> bool:
        return hashed == f"hashed_{plain}"


class FakeUnitOfWork:
    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def commit(self):
        pass


from app.domain.entities.system_setting import SystemSetting


class FakeSystemSettingRepository:
    def __init__(self):
        self.settings = {}

    async def get(self, key: str):
        val = self.settings.get(key)
        return SystemSetting(key=key, value=val) if val is not None else None

    async def set(self, key: str, value: str):
        self.settings[key] = value
        return SystemSetting(key=key, value=value)

    async def set_if_not_exists(self, key: str, value: str) -> bool:
        if key in self.settings:
            return False
        self.settings[key] = value
        return True

    async def delete(self, key: str) -> bool:
        return self.settings.pop(key, None) is not None


@pytest.mark.asyncio
async def test_register_initial_admin_use_case():
    user_repo = FakeUserRepository()
    space_repo = FakeSpaceRepository()
    system_setting_repo = FakeSystemSettingRepository()
    hasher = FakePasswordHasher()
    uow = FakeUnitOfWork()

    use_case = RegisterInitialAdminUseCase(user_repo, space_repo, system_setting_repo, hasher, uow)
    admin_user = await use_case.execute(full_name="Admin", pin="135790")
    assert admin_user.is_admin is True
    assert admin_user.full_name == "Admin"
    assert admin_user.hashed_pin == "hashed_135790"
    assert admin_user.avatar_color == "#3C6E4E"
    assert await user_repo.count() == 1

    # Second call must raise InvalidOperationException
    with pytest.raises(InvalidOperationException):
        await use_case.execute(full_name="Admin 2", pin="246801")


@pytest.mark.asyncio
async def test_a_new_member_cannot_take_an_active_members_name():
    """The picker tells members apart by name, however it's capitalised. A past member's name is free."""
    user_repo = FakeUserRepository([
        User(id="emma", full_name="Emma"),
        User(id="old-liam", full_name="Liam", is_active=False),
    ])
    use_case = CreateMemberUseCase(user_repo, FakeSpaceRepository(), FakePasswordHasher(), FakeUnitOfWork())

    with pytest.raises(NameTakenException):
        await use_case.execute(full_name="EMMA", pin="246801")

    liam = await use_case.execute(full_name="Liam", pin="246801")
    assert liam.full_name == "Liam"


@pytest.mark.asyncio
async def test_delete_sole_admin_is_prevented():
    admin = User(id="admin-1", full_name="Admin", hashed_pin="h", is_admin=True)
    user_repo = FakeUserRepository([admin])
    space_repo = FakeSpaceRepository()
    agent_repo = FakeAgentRepository()
    mem_repo = FakeMemoryRepository()
    uow = FakeUnitOfWork()

    use_case = DeleteMemberUseCase(user_repo, space_repo, agent_repo, mem_repo, uow)

    with pytest.raises(SoleAdminDeletionException):
        await use_case.execute(user_id_to_delete="admin-1", current_admin=admin)


@pytest.mark.asyncio
async def test_create_memory_blocked_if_secret_session():
    mem_repo = FakeMemoryRepository()
    session = ConversationSession(id="sess-secret", user_id="u1", agent_id="a1", title="Secret", is_secret=True)
    session_repo = FakeSessionRepository([session])
    agent = AgentPersonality(id="a1", slug="a1", name="Agent 1", system_prompt="p")
    agent_repo = FakeAgentRepository([agent])
    uow = FakeUnitOfWork()

    use_case = CreateMemoryUseCase(mem_repo, session_repo, agent_repo, uow)

    with pytest.raises(SecretModeViolationException):
        await use_case.execute(
            user_id="u1",
            agent_id="a1",
            scope="household",
            category="fact",
            content="Secret info",
            confidence=1.0,
            source_session_id="sess-secret",
        )


@pytest.mark.asyncio
async def test_get_memory_zero_leak_enforced():
    from app.domain.use_cases.memories.get_memory import GetMemoryUseCase
    from app.domain.exceptions import ZeroLeakViolationException

    mem = AgentMemory(id="m1", user_id="user-a", scope="personal", content="Private fact", category="fact")
    mem_repo = FakeMemoryRepository([mem])
    use_case = GetMemoryUseCase(mem_repo)

    user_a = User(id="user-a", full_name="Alice", hashed_pin="h")
    user_b = User(id="user-b", full_name="Bob", hashed_pin="h")

    # Owner can retrieve
    res = await use_case.execute("m1", user_a)
    assert res.id == "m1"

    # Non-owner cannot retrieve personal memory
    with pytest.raises(ZeroLeakViolationException):
        await use_case.execute("m1", user_b)

