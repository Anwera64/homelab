"""devices sign out on their own

Revision ID: 6f2a8d4c9e17
Revises: 4a7d2e9c1b36
Create Date: 2026-10-11 12:00:00.000000

Each signed-in device now gets a row. Its id is stamped into the device's tokens, so signing out
removes the row and only that device's tokens stop working (issue #94).
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = '6f2a8d4c9e17'
down_revision: Union[str, None] = '4a7d2e9c1b36'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'device_sessions',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('user_id', sa.String(length=36), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('last_seen_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
    )
    op.create_index(op.f('ix_device_sessions_user_id'), 'device_sessions', ['user_id'], unique=False)


def downgrade() -> None:
    op.drop_index(op.f('ix_device_sessions_user_id'), table_name='device_sessions')
    op.drop_table('device_sessions')
