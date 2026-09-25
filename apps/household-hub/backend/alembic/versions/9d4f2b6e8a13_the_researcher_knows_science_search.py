"""the researcher knows when to search science

`searxng_search` takes a category now: 'science' (arXiv, Wikipedia, Wikidata, Wolfram Alpha) or
'general'. The model chooses per question; the built-in Researcher's prompt says when each fits.
Only the untouched built-in moves: a prompt someone wrote for it stays theirs.

Revision ID: 9d4f2b6e8a13
Revises: 7c3e91a0b5d2
Create Date: 2026-09-25 10:00:00.000000
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = '9d4f2b6e8a13'
down_revision: Union[str, None] = '7c3e91a0b5d2'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


_BEFORE = (
    "You are the Household Academic & Document Researcher. You specialize in deep academic analysis, "
    "synthesizing complex documents, reading architectural papers, and extracting exact citations. "
    "You maintain an objective, thorough, and rigorous research tone."
)
_AFTER = (
    "You are the Household Academic & Document Researcher. You specialize in deep academic analysis, "
    "synthesizing complex documents, reading architectural papers, and extracting exact citations. "
    "You maintain an objective, thorough, and rigorous research tone. "
    "When you search, use category 'science' for papers, data and definitions, and 'general' "
    "for news, reports from NGOs and governments, and current events."
)


def _move(from_prompt: str, to_prompt: str) -> None:
    op.get_bind().execute(
        sa.text(
            "UPDATE agent_personalities SET system_prompt = :to "
            "WHERE slug = 'researcher' AND is_builtin = 1 AND system_prompt = :from"
        ),
        {"from": from_prompt, "to": to_prompt},
    )


def upgrade() -> None:
    _move(_BEFORE, _AFTER)


def downgrade() -> None:
    _move(_AFTER, _BEFORE)
