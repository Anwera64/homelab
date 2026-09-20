package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.GossipRemoteDataSource
import com.homelab.household.data.dto.GossipMilestoneDto
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.HouseholdMilestone
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The gossip repository maps DTOs to `HouseholdMilestone` and otherwise passes calls straight through. */
class GossipRepositoryTest {

    private val milestoneDto = GossipMilestoneDto(
        id = "ms-1",
        source_user_id = "emma",
        source_username = "Emma",
        reporting_agent_id = "a-1",
        reporting_agent_name = "Llama",
        summary = "Emma finished her first 5k run",
        created_at = "2026-09-13T00:00:00Z",
    )

    private val milestone = HouseholdMilestone(
        id = "ms-1",
        sourceUserId = "emma",
        sourceUsername = "Emma",
        reportingAgentId = "a-1",
        reportingAgentName = "Llama",
        summary = "Emma finished her first 5k run",
        createdAt = "2026-09-13T00:00:00Z",
    )

    private fun repository(remote: GossipRemoteDataSource) = GossipRepositoryImpl(remote = remote)

    // ---- listHouseholdMilestones --------------------------------------------

    @Test
    fun `GIVEN the hub reports a milestone WHEN the shared feed is asked for THEN it is mapped to a household milestone`() = runTest {
        // GIVEN
        val remote = mock<GossipRemoteDataSource>()
        everySuspend { remote.listHouseholdMilestones(20) } returns listOf(milestoneDto)

        // WHEN
        val milestones = repository(remote).listHouseholdMilestones(20)

        // THEN
        assertEquals(listOf(milestone), milestones)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN the household's milestones are asked for THEN the failure reaches the caller`() = runTest {
        // GIVEN
        val remote = mock<GossipRemoteDataSource>()
        everySuspend { remote.listHouseholdMilestones(20) } throws ServerOfflineException()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { repository(remote).listHouseholdMilestones(20) }
    }

    // ---- listUserAuditMilestones --------------------------------------------

    @Test
    fun `GIVEN a member auditing their own trail WHEN it is asked for THEN it is mapped to household milestones`() = runTest {
        // GIVEN
        val remote = mock<GossipRemoteDataSource>()
        everySuspend { remote.listUserAuditMilestones(50) } returns listOf(milestoneDto)

        // WHEN
        val milestones = repository(remote).listUserAuditMilestones(50)

        // THEN
        assertEquals(listOf(milestone), milestones)
    }

    // ---- revokeMilestone ------------------------------------------------------

    @Test
    fun `GIVEN a milestone a member wants forgotten WHEN it is revoked THEN the data source is asked to remove it`() = runTest {
        // GIVEN
        val remote = mock<GossipRemoteDataSource>()
        everySuspend { remote.revokeMilestone("ms-1") } returns Unit

        // WHEN
        repository(remote).revokeMilestone("ms-1")

        // THEN
        verifySuspend(VerifyMode.exactly(1)) { remote.revokeMilestone("ms-1") }
    }
}
