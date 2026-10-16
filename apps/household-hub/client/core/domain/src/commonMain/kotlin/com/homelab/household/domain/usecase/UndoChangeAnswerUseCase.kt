package com.homelab.household.domain.usecase

/** Undo after answering a change: everything is as before and the change is open again. */
fun interface UndoChangeAnswerUseCase {
    suspend operator fun invoke(changeId: String)
}
