"""reflection after the chat goes quiet

Revision ID: d7a1c3e9f402
Revises: c4f9a2d7e510
Create Date: 2026-10-15 23:30:00.000000

Reflection moves from every turn to the idle sweep (memory spec §2.2). Each chat keeps a marker: the
last message reflected, when, and what that run added, proposed and retired (§3.4). Memories,
vectors, proposals and chats are cleared once more, so the new reflection is tested from a clean
start rather than on what per-turn reflection wrote meanwhile (§3.3).
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'd7a1c3e9f402'
down_revision: Union[str, None] = 'c4f9a2d7e510'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("DELETE FROM memory_vectors")
    op.execute("DELETE FROM memory_proposals")
    op.execute("DELETE FROM agent_memories")
    op.execute("DELETE FROM chat_messages")
    op.execute("DELETE FROM conversation_sessions")

    with op.batch_alter_table('conversation_sessions', schema=None) as batch_op:
        batch_op.add_column(sa.Column('reflected_through_id', sa.String(length=36), nullable=True))
        batch_op.add_column(sa.Column('reflected_at', sa.DateTime(timezone=True), nullable=True))
        batch_op.add_column(sa.Column('last_reflection', sa.JSON(), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table('conversation_sessions', schema=None) as batch_op:
        batch_op.drop_column('last_reflection')
        batch_op.drop_column('reflected_at')
        batch_op.drop_column('reflected_through_id')
