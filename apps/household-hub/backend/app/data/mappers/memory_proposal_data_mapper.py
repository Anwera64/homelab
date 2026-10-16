from app.data.models.memory_proposal_model import MemoryProposalModel
from app.domain.entities.memory_proposal import MemoryProposal


class MemoryProposalDataMapper:
    @staticmethod
    def to_domain(model: MemoryProposalModel) -> MemoryProposal:
        return MemoryProposal(
            id=model.id,
            memory_id=model.memory_id,
            content=model.content,
            relation=model.relation,
            answerer_id=model.answerer_id,
            source_session_id=model.source_session_id,
            source_user_id=model.source_user_id,
            state=model.state,
            new_memory_id=model.new_memory_id,
            created_at=model.created_at,
            answered_at=model.answered_at,
        )

    @staticmethod
    def to_model(entity: MemoryProposal) -> MemoryProposalModel:
        return MemoryProposalModel(
            id=entity.id,
            memory_id=entity.memory_id,
            content=entity.content,
            relation=entity.relation,
            answerer_id=entity.answerer_id,
            source_session_id=entity.source_session_id,
            source_user_id=entity.source_user_id,
            state=entity.state,
            new_memory_id=entity.new_memory_id,
            created_at=entity.created_at,
            answered_at=entity.answered_at,
        )
