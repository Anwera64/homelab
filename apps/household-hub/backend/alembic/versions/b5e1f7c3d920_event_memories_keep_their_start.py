"""event memories keep their start

Revision ID: b5e1f7c3d920
Revises: a7d4c2e9f531
Create Date: 2026-10-19 17:30:00.000000

A memory linked to a calendar event keeps the start it was last matched to, so the periodic check
can tell the event moved, and whether the hub rebuilt its sentence from the event, which then holds
only the title and every reader adds the time in its own zone (memory spec §7, slice 5 PR 7).
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'b5e1f7c3d920'
down_revision: Union[str, None] = 'a7d4c2e9f531'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    with op.batch_alter_table('agent_memories', schema=None) as batch_op:
        batch_op.add_column(sa.Column('event_starts_at', sa.DateTime(timezone=True), nullable=True))
        batch_op.add_column(sa.Column('time_from_event', sa.String(length=8), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table('agent_memories', schema=None) as batch_op:
        batch_op.drop_column('time_from_event')
        batch_op.drop_column('event_starts_at')
