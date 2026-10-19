"""Fakes shared by the reflection tests (memory spec §2.2, §6)."""

import json
from datetime import datetime, timedelta, timezone
from typing import Dict, List

from app.domain.entities.llm_message import LLMResponse
from app.domain.entities.memory import AgentMemory
from app.domain.entities.session import ChatMessage, ConversationSession
from app.domain.entities.user import User

NOW = datetime(2026, 10, 15, 12, 0, tzinfo=timezone.utc)


class FakeLLM:
    def __init__(self, reply=None, content=None):
        self.reply = reply if reply is not None else {"facts": []}
        self.content = content
        self.calls = []

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None, keep_alive=None):
        self.calls.append({"messages": messages, "model": model, "keep_alive": keep_alive})
        content = self.content if self.content is not None else json.dumps(self.reply)
        return LLMResponse(content=content, usage={"prompt_tokens": 900, "completion_tokens": 300})

    @property
    def prompt(self) -> str:
        return "\n".join(m.content for m in self.calls[-1]["messages"])


class FakeSessions:
    def __init__(self, session: ConversationSession, messages: List[ChatMessage]):
        self.session = session
        self.messages = messages
        self.saved = []
        self.updated = []

    async def get_by_id(self, session_id):
        return self.session if session_id == self.session.id else None

    async def update(self, session):
        self.updated.append(session.title)
        self.session = session
        return session

    async def get_messages_after(self, session_id, after_id=None, limit=200):
        ids = [m.id for m in self.messages]
        start = ids.index(after_id) + 1 if after_id in ids else 0
        return self.messages[start:][-limit:]

    async def save_reflection(self, session_id, through_id, at, outcome):
        self.saved.append({"through": through_id, "at": at, "outcome": outcome})
        self.session.reflected_through_id = through_id
        self.session.reflected_at = at
        self.session.last_reflection = outcome


class FakeMemories:
    def __init__(self, memories: List[AgentMemory] = ()):
        self.memories = list(memories)
        self.created = []

    async def list_user_memories(self, user_id, scope=None, agent_id=None, category=None, active_only=True):
        return [m for m in self.memories if m.user_id == user_id and (scope is None or m.scope == scope)]

    async def list_household_memories(self, category=None):
        return [m for m in self.memories if m.scope == "household"]

    async def get_by_id(self, memory_id):
        return next((m for m in self.memories if m.id == memory_id), None)

    async def list_by_event(self, user_id, event_id):
        return [m for m in self.memories if m.user_id == user_id and m.event_id == event_id and m.is_active]

    async def create(self, memory):
        self.memories.append(memory)
        self.created.append(memory)
        return memory

    async def update(self, memory):
        return memory

    async def delete(self, memory_id):
        self.memories = [m for m in self.memories if m.id != memory_id]


class FakeProposals:
    def __init__(self, proposals=()):
        self.created = list(proposals)
        self.dropped_for = []
        self.updated = []

    async def create(self, proposal):
        self.created.append(proposal)
        return proposal

    async def get_by_id(self, proposal_id):
        return next((p for p in self.created if p.id == proposal_id), None)

    async def get_many(self, proposal_ids):
        return [p for p in self.created if p.id in proposal_ids]

    async def update(self, proposal):
        self.updated.append(proposal.state)
        return proposal

    async def list_pending_for_answerer(self, user_id):
        return [p for p in self.created if p.answerer_id == user_id and p.state == "pending"]

    async def list_pending_from_user(self, user_id):
        return [
            p for p in self.created
            if p.source_user_id == user_id and p.answerer_id != user_id and p.state == "pending"
        ]

    async def drop_pending_for(self, memory_id, origin=None):
        self.dropped_for.append(memory_id)
        dropped = [
            p.id for p in self.created
            if p.memory_id == memory_id and p.state == "pending" and (origin is None or p.origin == origin)
        ]
        for p in self.created:
            if p.id in dropped:
                p.state = "dropped"
        return dropped


class FakeUsers:
    def __init__(self, users: List[User]):
        self.users = {u.id: u for u in users}

    async def get_by_id(self, user_id):
        return self.users.get(user_id)

    async def list_all(self):
        return list(self.users.values())


class FakeEmbedder:
    def __init__(self, fail=False):
        self.fail = fail
        self.texts = []

    async def embed(self, texts):
        if self.fail:
            raise RuntimeError("embedder down")
        self.texts.extend(texts)
        return [[float(len(t))] for t in texts]


class FakeVectors:
    def __init__(self, scores: Dict[str, float] = None):
        self.scores = scores or {}

    async def best_scores(self, query, memory_ids, model):
        return {i: self.scores[i] for i in memory_ids if i in self.scores}


class FakeEmbedMemories:
    def __init__(self):
        self.embedded = []

    async def execute(self, memories):
        self.embedded.extend(memories)
        return len(memories)


class FakeUnitOfWork:
    def __init__(self):
        self.commits = 0

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def commit(self):
        self.commits += 1


def message(i: int, role: str, content: str, minutes: int = 0, **metadata) -> ChatMessage:
    return ChatMessage(
        id=f"msg-{i}",
        session_id="chat",
        role=role,
        content=content,
        metadata_json=dict(metadata),
        created_at=NOW - timedelta(minutes=60 - minutes),
    )


def memory(memory_id: str, content: str, user_id="emma", scope="personal", category="fact", minutes=0) -> AgentMemory:
    return AgentMemory(
        id=memory_id,
        user_id=user_id,
        scope=scope,
        category=category,
        content=content,
        created_at=NOW - timedelta(days=30, minutes=-minutes),
    )
