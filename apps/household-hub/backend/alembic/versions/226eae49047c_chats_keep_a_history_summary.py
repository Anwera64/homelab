"""chats keep a history summary

Revision ID: 226eae49047c
Revises: 5b1e7c2a9d40
Create Date: 2026-09-24 20:00:00.000000

A session now keeps a rolling summary of the messages it has compressed, plus the id of the last
one it covers, so a long chat does not have to resend everything on every turn.
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = '226eae49047c'
down_revision: Union[str, None] = '5b1e7c2a9d40'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    with op.batch_alter_table('conversation_sessions', schema=None) as batch_op:
        batch_op.add_column(sa.Column('history_summary', sa.Text(), nullable=True))
        batch_op.add_column(sa.Column('summarized_through_id', sa.String(length=36), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table('conversation_sessions', schema=None) as batch_op:
        batch_op.drop_column('summarized_through_id')
        batch_op.drop_column('history_summary')
