"""Turns can remember and forget, with cards that show the stored memory (memory spec §2.1)."""
from unittest.mock import MagicMock

from app.bootstrap.di import get_container
from app.core.config import Settings
from app.domain.use_cases.memories.remember_forget import ForgetUseCase, MemoryOnCardUseCase, RememberUseCase
from app.presentation.api import deps as pres_deps


def test_the_chat_turn_remembers_forgets_and_looks_memories_up_for_cards():
    turn = get_container(MagicMock())[pres_deps.get_process_chat_turn_use_case]

    assert isinstance(turn.tool_executor.remember, RememberUseCase)
    assert isinstance(turn.tool_executor.forget, ForgetUseCase)
    assert isinstance(turn.memory_card, MemoryOnCardUseCase)
    # Forgetting removes the memory's pending changes too, as ✕ does.
    assert turn.tool_executor.forget.retire.proposal_repo is not None


def test_the_match_bar_is_a_setting(monkeypatch):
    monkeypatch.setenv("MEMORY_MATCH_BAR", "0.7")

    assert Settings().MEMORY_MATCH_BAR == 0.7
    assert Settings.model_fields["MEMORY_MATCH_BAR"].default == 0.60
