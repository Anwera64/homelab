from typing import Dict, List

from app.data.datasources.memory_vector_data_source import IMemoryVectorDataSource
from app.domain.repositories.memory_vector_repository import IMemoryVectorRepository


class MemoryVectorRepositoryImpl(IMemoryVectorRepository):
    def __init__(self, data_source: IMemoryVectorDataSource):
        self.data_source = data_source

    async def save(self, memory_id: str, kind: str, model: str, vector: List[float]) -> None:
        await self.data_source.save(memory_id, kind, model, vector)

    async def missing(self, model: str) -> List[str]:
        return await self.data_source.missing(model)

    async def best_scores(self, query: List[float], memory_ids: List[str], model: str) -> Dict[str, float]:
        return await self.data_source.best_scores(query, memory_ids, model)
