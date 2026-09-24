"""the researcher warms up to 0.6

The built-in Researcher ran a thinking model at temperature 0.3, far below the 1.0 the model is
set up for, and sent garbled tool names ("searng_search", tool-call markup inside a name). Only
the untouched built-in moves: a temperature someone chose for it stays theirs.

Revision ID: 7c3e91a0b5d2
Revises: 226eae49047c
Create Date: 2026-09-24 20:10:00.000000
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = '7c3e91a0b5d2'
down_revision: Union[str, None] = '226eae49047c'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def _move(from_temperature: float, to_temperature: float) -> None:
    op.get_bind().execute(
        sa.text(
            "UPDATE agent_personalities SET temperature = :to "
            "WHERE slug = 'researcher' AND is_builtin = 1 AND temperature = :from"
        ),
        {"from": from_temperature, "to": to_temperature},
    )


def upgrade() -> None:
    _move(0.3, 0.6)


def downgrade() -> None:
    _move(0.6, 0.3)
