package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ProposalAnswer

/** Keep stored, Keep both or Use new on a change. It can be undone with [UndoChangeAnswerUseCase]. */
fun interface AnswerChangeUseCase {
    suspend operator fun invoke(
        changeId: String,
        answer: ProposalAnswer,
    )
}
