"""proposals remember their new memory

Revision ID: e3b7d2f1a804
Revises: d7a1c3e9f402
Create Date: 2026-10-16 18:30:00.000000

Keep both and Use new save the proposal's sentence as a new memory. The proposal records which one,
so Undo can take it away again (memory spec §6.1, slice 5 PR 5).
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'e3b7d2f1a804'
down_revision: Union[str, None] = 'd7a1c3e9f402'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    with op.batch_alter_table('memory_proposals', schema=None) as batch_op:
        batch_op.add_column(sa.Column('new_memory_id', sa.String(length=36), nullable=True))
        batch_op.create_foreign_key(
            'fk_memory_proposals_new_memory_id', 'agent_memories', ['new_memory_id'], ['id'], ondelete='SET NULL'
        )


def downgrade() -> None:
    with op.batch_alter_table('memory_proposals', schema=None) as batch_op:
        batch_op.drop_constraint('fk_memory_proposals_new_memory_id', type_='foreignkey')
        batch_op.drop_column('new_memory_id')
