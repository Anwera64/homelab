"""held remembers keep their end date

Revision ID: a7d4c2e9f531
Revises: f2c8e4a1b917
Create Date: 2026-10-18 20:30:00.000000

`remember` over another member's memory is held in the change until they answer, and saved only then,
so the change carries the end date the memory will have (memory spec §2.1, slice 5 PR 6).
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'a7d4c2e9f531'
down_revision: Union[str, None] = 'f2c8e4a1b917'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    with op.batch_alter_table('memory_proposals', schema=None) as batch_op:
        batch_op.add_column(sa.Column('expires_at', sa.DateTime(timezone=True), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table('memory_proposals', schema=None) as batch_op:
        batch_op.drop_column('expires_at')
