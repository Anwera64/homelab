package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.MemoryRemoteDataSource
import com.homelab.household.data.mapper.MemoryDataMapper
import com.homelab.household.data.mapper.toWire
import com.homelab.household.domain.model.ChangesToCheck
import com.homelab.household.domain.model.LookedOver
import com.homelab.household.domain.model.MemoryPage
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.domain.repository.MemoryRepository

/** What the agents remember. [remote] speaks DTOs; every one becomes a domain memory here. */
class MemoryRepositoryImpl(
    private val remote: MemoryRemoteDataSource,
) : MemoryRepository {
    override suspend fun getMemoryPage(): MemoryPage = MemoryDataMapper.toDomain(remote.getMemoryPage())

    override suspend fun removeMemory(memoryId: String) = remote.removeMemory(memoryId)

    override suspend fun restoreMemory(memoryId: String) = remote.restoreMemory(memoryId)

    override suspend fun getChangesToCheck(): ChangesToCheck = MemoryDataMapper.toDomain(remote.getChangesToCheck())

    override suspend fun answerChange(
        changeId: String,
        answer: ProposalAnswer,
    ) = remote.answerChange(changeId, answer.toWire())

    override suspend fun undoChangeAnswer(changeId: String) = remote.undoChangeAnswer(changeId)

    override suspend fun getLookedOver(sessionId: String): LookedOver =
        MemoryDataMapper.toDomain(remote.getLookedOver(sessionId))

    override suspend fun withdrawChange(changeId: String) = remote.withdrawChange(changeId)

    override suspend fun restoreChange(changeId: String) = remote.restoreChange(changeId)
}
