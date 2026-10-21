"""every calendar of the account

Revision ID: a3c6e8f1d274
Revises: b5e1f7c3d920
Create Date: 2026-10-22 12:00:00.000000

A connection reads every calendar of the account the member picked, not one (#252). The calendars
are kept per connection with the member's choice; an existing connection gets them on its first
read, with the same defaults as a new one. Event memories keep which calendar their event is on;
older links have none and find theirs on the next check.
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'a3c6e8f1d274'
down_revision: Union[str, None] = 'b5e1f7c3d920'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'calendar_sources',
        sa.Column('credential_id', sa.String(length=36), nullable=False),
        sa.Column('key', sa.String(length=16), nullable=False),
        sa.Column('url', sa.String(length=1024), nullable=False),
        sa.Column('name', sa.String(length=255), nullable=False),
        sa.Column('writable', sa.Boolean(), nullable=False),
        sa.Column('picked', sa.Boolean(), nullable=False),
        sa.Column('position', sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(['credential_id'], ['calendar_credentials.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('credential_id', 'key'),
    )
    with op.batch_alter_table('agent_memories', schema=None) as batch_op:
        batch_op.add_column(sa.Column('event_calendar', sa.String(length=16), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table('agent_memories', schema=None) as batch_op:
        batch_op.drop_column('event_calendar')
    op.drop_table('calendar_sources')
