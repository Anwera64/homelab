package com.homelab.household.data.mapper

import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.model.StoredMemory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A `remember` or `forget` card's details (memory spec §2.1): the sentence, whether it is the
 * household's, and the stored memory the hub looked up for the card.
 */
class MemoryDetailsDataMapperTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun `GIVEN a remember for the household WHEN read THEN its sentence and whose it is are kept`() {
        val details =
            ProposalDetailsDataMapper.fromJson(
                "remember",
                json("""{"content": " Sam's birthday is 3 March. ", "scope": "household", "category": "milestone"}"""),
            )

        assertEquals(ProposalDetails.Memory(content = "Sam's birthday is 3 March.", household = true), details)
    }

    @Test
    fun `GIVEN a remember with no scope WHEN read THEN it is the members own`() {
        val details = ProposalDetailsDataMapper.fromJson("remember", json("""{"content": "Emma likes tea."}"""))

        assertEquals(ProposalDetails.Memory(content = "Emma likes tea.", household = false), details)
    }

    @Test
    fun `GIVEN a remember over Liams memory WHEN read THEN the stored memory shows who said it and when`() {
        val details =
            ProposalDetailsDataMapper.fromJson(
                "remember",
                json(
                    """{"content": "The boiler moved to November.", "replaces": "boiler", "memory_id": "boiler",
                    "stored": {"content": "The boiler service is booked yearly in October.", "said_by": "Liam",
                    "mine": false, "agent": "Planner", "created_at": "2026-03-04T10:00:00+00:00"}}""",
                ),
            )

        assertEquals(
            StoredMemory(
                content = "The boiler service is booked yearly in October.",
                saidBy = "Liam",
                mine = false,
                agentName = "Planner",
                addedAt = Instant.parse("2026-03-04T10:00:00Z"),
            ),
            (details as ProposalDetails.Memory).stored,
        )
    }

    @Test
    fun `GIVEN a forget WHEN read THEN the card shows the stored sentence rather than the quote`() {
        val details =
            ProposalDetailsDataMapper.fromJson(
                "forget",
                json(
                    """{"memory": "the jury thing", "memory_id": "jury",
                    "stored": {"content": "Studio Jury on Friday 17 October.", "said_by": "Emma", "mine": true, "agent": null, "created_at": null}}""",
                ),
            )

        assertEquals(
            ProposalDetails.Memory(
                content = "Studio Jury on Friday 17 October.",
                household = null,
                stored = StoredMemory("Studio Jury on Friday 17 October.", saidBy = "Emma", mine = true),
            ),
            details,
        )
    }

    @Test
    fun `GIVEN a forget with nothing looked up WHEN read THEN the quote is shown`() {
        val details = ProposalDetailsDataMapper.fromJson("forget", json("""{"memory": "the jury thing"}"""))

        assertEquals(ProposalDetails.Memory(content = "the jury thing", household = null), details)
    }

    @Test
    fun `GIVEN a stored memory the hub sent half of WHEN read THEN it is left out`() {
        val details =
            ProposalDetailsDataMapper.fromJson("remember", json("""{"content": "x", "stored": {"said_by": "Liam"}}"""))

        assertEquals(ProposalDetails.Memory(content = "x", household = false), details)
    }

    @Test
    fun `GIVEN a reworded sentence moved to household WHEN written back THEN the hub gets both`() {
        val arguments =
            ProposalDetailsDataMapper.toArguments(ProposalDetails.Memory(content = "Sam is vegan.", household = true))

        assertEquals(json("""{"content": "Sam is vegan.", "scope": "household"}"""), arguments)
    }

    @Test
    fun `GIVEN only the switch moved to yours WHEN written back THEN only the scope is sent`() {
        val arguments = ProposalDetailsDataMapper.toArguments(ProposalDetails.Memory(content = null, household = false))

        assertEquals(json("""{"scope": "personal"}"""), arguments)
    }
}
