"""agents point at models instead of naming them

Revision ID: 5b1e7c2a9d40
Revises: 180c460dc76b
Create Date: 2026-09-23 12:00:00.000000

Each agent used to carry the inference server's name for its model. Now models are rows, the
household has one default, and an agent either follows it (no pin) or points at one row. The model
the built-in agents used becomes the default; any other name an agent used becomes its own row.
"""

from datetime import datetime, timezone
from typing import Sequence, Union
import uuid

from alembic import op
import sqlalchemy as sa


revision: str = '5b1e7c2a9d40'
down_revision: Union[str, None] = '180c460dc76b'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


_FK_NAME = "fk_agent_personalities_llm_model_id"


def _default_alias(conn) -> Union[str, None]:
    """The built-ins' model, or failing that the one most agents use."""
    for builtin_only in (True, False):
        where = "WHERE is_builtin = 1 " if builtin_only else ""
        row = conn.execute(
            sa.text(
                f"SELECT model_alias FROM agent_personalities {where}"
                "GROUP BY model_alias ORDER BY COUNT(*) DESC, model_alias LIMIT 1"
            )
        ).first()
        if row:
            return row[0]
    return None


def upgrade() -> None:
    op.create_table(
        'llm_models',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('provider_model', sa.String(length=128), nullable=False),
        sa.Column('display_name', sa.String(length=128), nullable=False),
        sa.Column('is_default', sa.Boolean(), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('provider_model'),
    )

    with op.batch_alter_table('agent_personalities') as batch_op:
        batch_op.add_column(sa.Column('llm_model_id', sa.String(length=36), nullable=True))
        batch_op.create_foreign_key(_FK_NAME, 'llm_models', ['llm_model_id'], ['id'], ondelete='SET NULL')

    conn = op.get_bind()
    default_alias = _default_alias(conn)
    now = datetime.now(timezone.utc)
    aliases = [row[0] for row in conn.execute(sa.text("SELECT DISTINCT model_alias FROM agent_personalities"))]
    for alias in aliases:
        model_id = str(uuid.uuid4())
        conn.execute(
            sa.text(
                "INSERT INTO llm_models (id, provider_model, display_name, is_default, created_at, updated_at) "
                "VALUES (:id, :alias, :alias, :is_default, :now, :now)"
            ).bindparams(sa.bindparam("now", type_=sa.DateTime(timezone=True))),
            {"id": model_id, "alias": alias, "is_default": alias == default_alias, "now": now},
        )
        if alias != default_alias:
            conn.execute(
                sa.text("UPDATE agent_personalities SET llm_model_id = :id WHERE model_alias = :alias"),
                {"id": model_id, "alias": alias},
            )

    with op.batch_alter_table('agent_personalities') as batch_op:
        batch_op.drop_column('model_alias')


def downgrade() -> None:
    with op.batch_alter_table('agent_personalities') as batch_op:
        batch_op.add_column(sa.Column('model_alias', sa.String(length=64), nullable=True))

    conn = op.get_bind()
    conn.execute(
        sa.text(
            "UPDATE agent_personalities SET model_alias = COALESCE("
            "(SELECT provider_model FROM llm_models WHERE llm_models.id = agent_personalities.llm_model_id), "
            "(SELECT provider_model FROM llm_models WHERE is_default = 1))"
        )
    )

    with op.batch_alter_table('agent_personalities') as batch_op:
        batch_op.alter_column('model_alias', existing_type=sa.String(length=64), nullable=False)
        batch_op.drop_constraint(_FK_NAME, type_='foreignkey')
        batch_op.drop_column('llm_model_id')

    op.drop_table('llm_models')
