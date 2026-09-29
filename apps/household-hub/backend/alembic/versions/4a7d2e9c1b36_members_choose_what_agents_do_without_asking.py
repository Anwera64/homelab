"""members choose what agents do without asking

Revision ID: 4a7d2e9c1b36
Revises: 3e8b5c1d7f20
Create Date: 2026-09-28 18:00:00.000000

Auto-approve used to be one flag the phone sent with each message, and the phone always sent false.
Each member now keeps a choice per write action (adding events, creating notes...), and a row only
exists for an action they have set. Removing an event and replacing a note always ask, so the hub
never saves them as automatic.
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = '4a7d2e9c1b36'
down_revision: Union[str, None] = '3e8b5c1d7f20'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'tool_approval_preferences',
        sa.Column('user_id', sa.String(length=36), nullable=False),
        sa.Column('tool', sa.String(length=64), nullable=False),
        sa.Column('action', sa.String(length=32), nullable=False),
        sa.Column('auto', sa.Boolean(), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('user_id', 'tool', 'action'),
    )


def downgrade() -> None:
    op.drop_table('tool_approval_preferences')
