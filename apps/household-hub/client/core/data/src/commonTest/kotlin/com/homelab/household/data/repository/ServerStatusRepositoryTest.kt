package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.ServerStatusRemoteDataSource
import com.homelab.household.domain.model.ServerStatus
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The thinnest repository in the module, and deliberately so: whether the hub is reachable is not a
 * question anything needs mapped or decided. It was the one that already had this shape before the
 * rework, which is where the shape came from.
 */
class ServerStatusRepositoryTest {

    @Test
    fun `GIVEN a hub that answers WHEN its health is checked THEN the answer is passed straight through`() = runTest {
        // GIVEN
        val remote = mock<ServerStatusRemoteDataSource>()
        everySuspend { remote.checkHealth() } returns ServerStatus.Online(latencyMs = 12)

        // WHEN
        val status = ServerStatusRepositoryImpl(remote).checkHealth()

        // THEN
        assertEquals(ServerStatus.Online(latencyMs = 12), status)
    }

    @Test
    fun `GIVEN a hub going down and coming back WHEN its status is watched THEN every change is passed straight through`() = runTest {
        // GIVEN
        val remote = mock<ServerStatusRemoteDataSource>()
        val changes = listOf(
            ServerStatus.Online(latencyMs = 8),
            ServerStatus.Offline(reason = "Connection refused"),
            ServerStatus.Online(latencyMs = 40),
        )
        every { remote.observeStatus(any()) } returns flowOf(*changes.toTypedArray())

        // WHEN
        val seen = ServerStatusRepositoryImpl(remote).observeServerStatus().toList()

        // THEN
        assertEquals(changes, seen)
    }
}
