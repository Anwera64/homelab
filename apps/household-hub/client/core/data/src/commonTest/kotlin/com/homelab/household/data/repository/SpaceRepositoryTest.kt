package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.SpaceRemoteDataSource
import com.homelab.household.data.dto.SpaceReadDto
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.Space
import com.homelab.household.domain.model.SpaceType
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

/**
 * The spaces repository maps DTOs to `Space`, and turns a settings change's `Any?` values into the
 * wire's strings before it reaches the data source.
 */
class SpaceRepositoryTest {
    private val householdDto =
        SpaceReadDto(
            id = "sp-household",
            name = "The Household",
            type = "household",
            settings = mapOf("theme" to "dark"),
            created_at = "2026-09-13T00:00:00Z",
        )

    private val household =
        Space(
            id = "sp-household",
            name = "The Household",
            type = SpaceType.HOUSEHOLD,
            settings = mapOf("theme" to "dark"),
            createdAt = "2026-09-13T00:00:00Z",
        )

    private val personalDto =
        SpaceReadDto(
            id = "sp-personal-emma",
            name = "Emma's space",
            type = "personal",
            owner_id = "emma",
            created_at = "2026-09-13T00:00:00Z",
        )

    private fun repository(remote: SpaceRemoteDataSource) = SpaceRepositoryImpl(remote = remote)

    // ---- getPersonalSpace -----------------------------------------------------

    @Test
    fun `GIVEN a member's own space WHEN it is asked for THEN it is mapped to a personal space`() =
        runTest {
            // GIVEN
            val remote = mock<SpaceRemoteDataSource>()
            everySuspend { remote.getPersonalSpace() } returns personalDto

            // WHEN
            val space = repository(remote).getPersonalSpace()

            // THEN
            assertEquals(SpaceType.PERSONAL, space.type)
            assertEquals("sp-personal-emma", space.id)
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a member's own space is asked for THEN the failure reaches the caller`() =
        runTest {
            // GIVEN
            val remote = mock<SpaceRemoteDataSource>()
            everySuspend { remote.getPersonalSpace() } throws ServerOfflineException()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> { repository(remote).getPersonalSpace() }
        }

    // ---- getHouseholdSpace ------------------------------------------------

    @Test
    fun `GIVEN the space everybody shares WHEN it is asked for THEN it is mapped to a household space`() =
        runTest {
            // GIVEN
            val remote = mock<SpaceRemoteDataSource>()
            everySuspend { remote.getHouseholdSpace() } returns householdDto

            // WHEN
            val space = repository(remote).getHouseholdSpace()

            // THEN
            assertEquals(household, space)
        }

    // ---- updateSpaceSettings -----------------------------------------------

    @Test
    fun `GIVEN settings holding numbers and booleans and nulls WHEN they are saved THEN they reach the data source as strings`() =
        runTest {
            // GIVEN
            val remote = mock<SpaceRemoteDataSource>()
            val expectedWire = mapOf("theme" to "dark", "quiet_hours" to "22", "notify" to "true", "note" to "")
            everySuspend { remote.updateSpaceSettings("sp-household", expectedWire) } returns householdDto

            // WHEN
            val space =
                repository(remote).updateSpaceSettings(
                    "sp-household",
                    mapOf("theme" to "dark", "quiet_hours" to 22, "notify" to true, "note" to null),
                )

            // THEN
            assertEquals(household, space)
            verifySuspend(VerifyMode.exactly(1)) { remote.updateSpaceSettings("sp-household", expectedWire) }
        }
}
