package com.homelab.household.presentation.memories

import com.homelab.household.domain.exception.ChangeWithdrawnException
import com.homelab.household.domain.model.ChangeCard
import com.homelab.household.domain.model.DeviceTimeZone
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.domain.usecase.AnswerChangeUseCase
import com.homelab.household.domain.usecase.UndoChangeAnswerUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Where a change card is: open to answer, saving one answer, or answered with Undo in place. */
sealed interface ChangeStatus {
    /** [failed]: the last answer didn't save; "Couldn't save your choice. Nothing changed." */
    data class Open(
        val failed: Boolean = false,
    ) : ChangeStatus

    data class Saving(
        val answer: ProposalAnswer,
    ) : ChangeStatus

    /** [undoFailed]: Undo didn't reach the hub, so the answer stands. */
    data class Done(
        val answer: ProposalAnswer,
        val undoFailed: Boolean = false,
    ) : ChangeStatus

    /** Its asker took the sentence back before it was answered: [message] is the hub's "Emma took this back." */
    data class Withdrawn(
        val message: String,
    ) : ChangeStatus
}

/** One change as its card draws it: ages and times already worked out for the member's phone. */
data class ChangeRow(
    val card: ChangeCard,
    val storedAge: MemoryAge,
    val readAge: MemoryAge,
    val readTime: LocalTime,
    val status: ChangeStatus = ChangeStatus.Open(),
)

@OptIn(ExperimentalTime::class)
fun changeRowOf(
    card: ChangeCard,
    deviceTimeZone: DeviceTimeZone,
    clock: Clock,
): ChangeRow {
    val now = clock.now()
    val offset = deviceTimeZone.offsetSecondsAt(now)
    return ChangeRow(
        card = card,
        storedAge = memoryAge(card.stored.createdAt, now, offset),
        readAge = memoryAge(card.readAt, now, offset),
        readTime = localTime(card.readAt, deviceTimeZone.offsetSecondsAt(card.readAt)),
    )
}

/** The changes still to answer: open, saving or failed, but not done or taken back. */
fun List<ChangeRow>.openCount(): Int =
    count { it.card.canAnswer && it.status !is ChangeStatus.Done && it.status !is ChangeStatus.Withdrawn }

/** The read-only changes, grouped under "Waiting for <name>". */
fun List<ChangeRow>.byAnswerer(): List<Pair<String, List<ChangeRow>>> = groupBy { it.card.answererName }.toList()

/**
 * Answering and Undo on change cards, shared by Changes to check and the chat sheet so the two can't
 * drift. Only cards the member can answer take a tap, and only one answer at a time.
 */
class ChangeAnswers(
    private val scope: CoroutineScope,
    private val answerChange: AnswerChangeUseCase,
    private val undoChangeAnswer: UndoChangeAnswerUseCase,
    private val rows: () -> List<ChangeRow>,
    private val update: ((List<ChangeRow>) -> List<ChangeRow>) -> Unit,
    private val onSettled: () -> Unit = {},
) {
    fun answer(
        changeId: String,
        answer: ProposalAnswer,
    ) {
        val row = rows().firstOrNull { it.card.id == changeId } ?: return
        if (!row.card.canAnswer || row.status !is ChangeStatus.Open) return
        set(changeId, ChangeStatus.Saving(answer))
        scope.launch {
            runCatchingSafe { answerChange(changeId, answer) }.fold(
                onSuccess = { set(changeId, ChangeStatus.Done(answer)) },
                onFailure = { error ->
                    val status =
                        if (error is ChangeWithdrawnException) {
                            ChangeStatus.Withdrawn(error.message.orEmpty())
                        } else {
                            ChangeStatus.Open(failed = true)
                        }
                    set(changeId, status)
                },
            )
            onSettled()
        }
    }

    fun undo(changeId: String) {
        val done = rows().firstOrNull { it.card.id == changeId }?.status as? ChangeStatus.Done ?: return
        set(changeId, ChangeStatus.Open())
        scope.launch {
            runCatchingSafe { undoChangeAnswer(changeId) }.onFailure {
                set(changeId, done.copy(undoFailed = true))
            }
            onSettled()
        }
    }

    private fun set(
        changeId: String,
        status: ChangeStatus,
    ) = update { rows -> rows.map { if (it.card.id == changeId) it.copy(status = status) else it } }
}
