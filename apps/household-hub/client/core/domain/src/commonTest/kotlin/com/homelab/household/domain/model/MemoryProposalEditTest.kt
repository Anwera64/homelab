package com.homelab.household.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Editing a Remember card before approving (memory spec §2.1): the sentence and whether it is the
 * household's open as fields. A Forget card is never edited, and a sentence kept beside another
 * member's memory is the household's whatever the switch says, so it has no switch.
 */
class MemoryProposalEditTest {
    private fun remember(
        household: Boolean = false,
        stored: StoredMemory? = null,
    ) = AnswerPart.Proposal(
        "m-1",
        HubTool.REMEMBER,
        ToolAction.Create,
        ProposalDetails.Memory(content = "Sam is vegan now.", household = household, stored = stored),
    )

    private val liams = StoredMemory(content = "Sam is vegetarian.", saidBy = "Liam", mine = false)

    @Test
    fun `GIVEN a remember WHEN edited THEN its sentence and whose it is open as fields`() {
        assertEquals(listOf(EditField.What, EditField.Shared), editableFields(remember()))
    }

    @Test
    fun `GIVEN a remember kept beside another members memory WHEN edited THEN only its sentence opens`() {
        assertEquals(listOf(EditField.What), editableFields(remember(stored = liams)))
    }

    @Test
    fun `GIVEN a forget WHEN edited THEN nothing opens`() {
        val forget =
            AnswerPart.Proposal(
                "m-2",
                HubTool.FORGET,
                ToolAction.Delete,
                ProposalDetails.Memory("Jury", household = false),
            )

        assertEquals(emptyList(), editableFields(forget))
    }

    @Test
    fun `GIVEN a remember WHEN its fields start THEN they hold the sentence and whose it is`() {
        val values = proposedValues(remember(household = true))

        assertEquals("Sam is vegan now.", values.title)
        assertEquals(true, values.household)
    }

    @Test
    fun `GIVEN only the switch moved to household WHEN approved THEN only that is sent`() {
        val card = remember()

        val edit = editProposal(card, proposedValues(card).copy(household = true))

        assertEquals(setOf(EditField.Shared), edit.changed)
        assertEquals(ProposalDetails.Memory(content = null, household = true), edit.edited)
    }

    @Test
    fun `GIVEN a reworded sentence WHEN approved THEN the new words are sent and whose it is is not`() {
        val card = remember()

        val edit = editProposal(card, proposedValues(card).copy(title = " Sam eats no animal products. "))

        assertEquals(ProposalDetails.Memory(content = "Sam eats no animal products.", household = null), edit.edited)
    }

    @Test
    fun `GIVEN an emptied sentence WHEN approved THEN nothing is sent`() {
        val card = remember()

        val edit = editProposal(card, proposedValues(card).copy(title = "  "))

        assertEquals(setOf(EditField.What), edit.invalid)
        assertNull(edit.edited)
    }

    @Test
    fun `GIVEN an edit WHEN laid over the card THEN the card shows the edited sentence and keeps the rest`() {
        val details = remember(stored = liams).details

        val shown = details.withEdit(ProposalDetails.Memory(content = "Sam is vegan.", household = null))

        assertEquals(ProposalDetails.Memory("Sam is vegan.", household = false, stored = liams), shown)
    }
}
