"""proposals know where they came from

Revision ID: f2c8e4a1b917
Revises: e3b7d2f1a804
Create Date: 2026-10-18 12:00:00.000000

`remember` on another member's memory saves its sentence first and asks them which one is right, so
its answers mean something else than reflection's (memory spec §2.1, slice 5 PR 6).
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'f2c8e4a1b917'
down_revision: Union[str, None] = 'e3b7d2f1a804'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    with op.batch_alter_table('memory_proposals', schema=None) as batch_op:
        batch_op.add_column(
            sa.Column('origin', sa.String(length=16), server_default='reflection', nullable=False)
        )


def downgrade() -> None:
    with op.batch_alter_table('memory_proposals', schema=None) as batch_op:
        batch_op.drop_column('origin')
