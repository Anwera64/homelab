"""memories have vectors

Revision ID: c4f9a2d7e510
Revises: b8e3f1a6c925
Create Date: 2026-10-15 22:00:00.000000

Each memory is embedded so recall can match it by meaning (memory spec §4). An ordinary table with a
foreign key; nothing existing is touched, and the hub fills in vectors for stored memories on start.
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'c4f9a2d7e510'
down_revision: Union[str, None] = 'b8e3f1a6c925'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'memory_vectors',
        sa.Column('memory_id', sa.String(length=36), nullable=False),
        sa.Column('kind', sa.String(length=16), nullable=False),
        sa.Column('model', sa.String(length=128), nullable=False),
        sa.Column('vector', sa.LargeBinary(), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['memory_id'], ['agent_memories.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('memory_id', 'kind'),
    )
    op.create_index(op.f('ix_memory_vectors_model'), 'memory_vectors', ['model'], unique=False)


def downgrade() -> None:
    op.drop_index(op.f('ix_memory_vectors_model'), table_name='memory_vectors')
    op.drop_table('memory_vectors')
