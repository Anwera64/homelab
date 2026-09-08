from typing import List, Optional
from app.domain.entities.memory import AgentMemory
from app.domain.repositories.memory_repository import IMemoryRepository
from app.data.datasources.memory_data_source import IMemoryDataSource
from app.data.mappers.memory_data_mapper import MemoryDataMapper


class MemoryRepositoryImpl(IMemoryRepository):
    def __init__(self, data_source: IMemoryDataSource, mapper: MemoryDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def list_user_memories(
        self,
        user_id: str,
        scope: Optional[str] = None,
        agent_id: Optional[str] = None,
        category: Optional[str] = None,
        active_only: bool = True,
    ) -> List[AgentMemory]:
        models = await self.data_source.list_user_memories(
            user_id=user_id,
            scope=scope,
            agent_id=agent_id,
            category=category,
            active_only=active_only,
        )
        return [self.mapper.to_domain(m) for m in models]

    async def list_household_memories(
        self,
        category: Optional[str] = None,
    ) -> List[AgentMemory]:
        models = await self.data_source.list_household_memories(category=category)
        return [self.mapper.to_domain(m) for m in models]

    async def get_by_id(self, memory_id: str) -> Optional[AgentMemory]:
        model = await self.data_source.get_by_id(memory_id)
        return self.mapper.to_domain(model) if model else None

    async def create(self, memory: AgentMemory) -> AgentMemory:
        model = self.mapper.to_model(memory)
        created = await self.data_source.create(model)
        return self.mapper.to_domain(created)

    async def update(self, memory: AgentMemory) -> AgentMemory:
        model = await self.data_source.get_by_id(memory.id)
        if model:
            model.content = memory.content
            model.confidence = memory.confidence
            model.is_active = memory.is_active
            updated = await self.data_source.update(model)
            return self.mapper.to_domain(updated)
        else:
            model = self.mapper.to_model(memory)
            updated = await self.data_source.update(model)
            return self.mapper.to_domain(updated)

    async def delete(self, memory_id: str) -> None:
        await self.data_source.delete(memory_id)

    async def delete_personal_memories(self, user_id: str) -> None:
        await self.data_source.delete_personal_memories(user_id)

    async def reassign_household_memories(self, from_user_id: str, to_user_id: str) -> None:
        await self.data_source.reassign_household_memories(from_user_id, to_user_id)
