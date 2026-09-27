"""calendars sign in with google

Revision ID: 3e8b5c1d7f20
Revises: 9d4f2b6e8a13
Create Date: 2026-09-27 20:00:00.000000

Google Calendar only takes a Google sign-in, so a calendar can now hold OAuth tokens instead of a
password: the refresh token, when the access token (kept in encrypted_secret) runs out, and whether
Google has stopped honouring the sign-in. Every calendar already connected used a password.
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = '3e8b5c1d7f20'
down_revision: Union[str, None] = '9d4f2b6e8a13'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    with op.batch_alter_table('calendar_credentials', schema=None) as batch_op:
        batch_op.add_column(sa.Column('auth_kind', sa.String(length=16), server_default='password', nullable=False))
        batch_op.add_column(sa.Column('encrypted_refresh_token', sa.Text(), nullable=True))
        batch_op.add_column(sa.Column('token_expires_at', sa.DateTime(timezone=True), nullable=True))
        batch_op.add_column(sa.Column('needs_reconnect', sa.Boolean(), server_default=sa.false(), nullable=False))


def downgrade() -> None:
    with op.batch_alter_table('calendar_credentials', schema=None) as batch_op:
        batch_op.drop_column('needs_reconnect')
        batch_op.drop_column('token_expires_at')
        batch_op.drop_column('encrypted_refresh_token')
        batch_op.drop_column('auth_kind')
