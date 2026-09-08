package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.repository.ServerStatusRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerStatusUseCasesTest {

    private val statusRepo = mockk<ServerStatusRepository>()
    private val observeStatusUseCase = ObserveServerStatusUseCase(statusRepo)
    private val checkHealthUseCase = CheckServerHealthUseCase(statusRepo)

    @Test
    fun observe_status_emits_flow_of_server_statuses() = runTest {
        val statuses = listOf(
            ServerStatus.Connecting,
            ServerStatus.Online(latencyMs = 12),
            ServerStatus.Offline(reason = "Connection refused")
        )
        every { statusRepo.observeServerStatus() } returns flowOf(*statuses.toTypedArray())

        val result = observeStatusUseCase().toList()

        assertEquals(3, result.size)
        assertTrue(result[0] is ServerStatus.Connecting)
        assertTrue(result[1] is ServerStatus.Online)
        assertEquals(12L, (result[1] as ServerStatus.Online).latencyMs)
        assertTrue(result[2] is ServerStatus.Offline)
    }

    @Test
    fun check_health_returns_latest_status() = runTest {
        coEvery { statusRepo.checkHealth() } returns ServerStatus.Online(latencyMs = 8)

        val status = checkHealthUseCase()

        assertTrue(status is ServerStatus.Online)
        assertEquals(8L, (status as ServerStatus.Online).latencyMs)
    }
}
