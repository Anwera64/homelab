"""one memory store

Revision ID: b8e3f1a6c925
Revises: 6f2a8d4c9e17
Create Date: 2026-10-14 20:00:00.000000

Memories and shared facts become one store (memory spec §3). `agent_memories` gains what the later
slice 5 PRs need, `memory_proposals` holds pending changes, and `gossip_milestones` goes: a
household memory is the bus (§3.2). The hub starts with no chats and no memories: it is a development
environment, and an old chat would wait to be reflected without a marker (§3.3).
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'b8e3f1a6c925'
down_revision: Union[str, None] = '6f2a8d4c9e17'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    with op.batch_alter_table('gossip_milestones', schema=None) as batch_op:
        batch_op.drop_index(batch_op.f('ix_gossip_milestones_target_scope'))
        batch_op.drop_index(batch_op.f('ix_gossip_milestones_source_user_id'))
        batch_op.drop_index(batch_op.f('ix_gossip_milestones_reporting_agent_id'))
        batch_op.drop_index(batch_op.f('ix_gossip_milestones_is_active'))
        batch_op.drop_index(batch_op.f('ix_gossip_milestones_expires_at'))
        batch_op.drop_index(batch_op.f('ix_gossip_milestones_category'))
    op.drop_table('gossip_milestones')

    op.execute("DELETE FROM chat_messages")
    op.execute("DELETE FROM agent_memories")
    op.execute("DELETE FROM conversation_sessions")

    with op.batch_alter_table('agent_memories', schema=None) as batch_op:
        batch_op.add_column(sa.Column('expires_at', sa.DateTime(timezone=True), nullable=True))
        batch_op.add_column(
            sa.Column('origin', sa.String(length=16), server_default='reflection', nullable=False)
        )
        batch_op.add_column(sa.Column('relevant_for', sa.JSON(), nullable=True))
        batch_op.add_column(sa.Column('event_id', sa.String(length=255), nullable=True))
        batch_op.add_column(sa.Column('replaced_by_id', sa.String(length=36), nullable=True))
        batch_op.add_column(sa.Column('retired_at', sa.DateTime(timezone=True), nullable=True))
        batch_op.add_column(sa.Column('moved_from', sa.DateTime(timezone=True), nullable=True))
        batch_op.create_index(batch_op.f('ix_agent_memories_event_id'), ['event_id'], unique=False)
        batch_op.create_foreign_key(
            'fk_agent_memories_replaced_by_id', 'agent_memories', ['replaced_by_id'], ['id'], ondelete='SET NULL'
        )

    op.create_table(
        'memory_proposals',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('memory_id', sa.String(length=36), nullable=False),
        sa.Column('content', sa.Text(), nullable=False),
        sa.Column('relation', sa.String(length=32), nullable=False),
        sa.Column('answerer_id', sa.String(length=36), nullable=False),
        sa.Column('source_session_id', sa.String(length=36), nullable=True),
        sa.Column('source_user_id', sa.String(length=36), nullable=True),
        sa.Column('state', sa.String(length=16), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('answered_at', sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(['memory_id'], ['agent_memories.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['answerer_id'], ['users.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['source_session_id'], ['conversation_sessions.id'], ondelete='SET NULL'),
        sa.ForeignKeyConstraint(['source_user_id'], ['users.id'], ondelete='SET NULL'),
        sa.PrimaryKeyConstraint('id'),
    )
    op.create_index(op.f('ix_memory_proposals_memory_id'), 'memory_proposals', ['memory_id'], unique=False)
    op.create_index(op.f('ix_memory_proposals_answerer_id'), 'memory_proposals', ['answerer_id'], unique=False)
    op.create_index(op.f('ix_memory_proposals_state'), 'memory_proposals', ['state'], unique=False)


def downgrade() -> None:
    op.drop_index(op.f('ix_memory_proposals_state'), table_name='memory_proposals')
    op.drop_index(op.f('ix_memory_proposals_answerer_id'), table_name='memory_proposals')
    op.drop_index(op.f('ix_memory_proposals_memory_id'), table_name='memory_proposals')
    op.drop_table('memory_proposals')

    with op.batch_alter_table('agent_memories', schema=None) as batch_op:
        batch_op.drop_constraint('fk_agent_memories_replaced_by_id', type_='foreignkey')
        batch_op.drop_index(batch_op.f('ix_agent_memories_event_id'))
        batch_op.drop_column('moved_from')
        batch_op.drop_column('retired_at')
        batch_op.drop_column('replaced_by_id')
        batch_op.drop_column('event_id')
        batch_op.drop_column('relevant_for')
        batch_op.drop_column('origin')
        batch_op.drop_column('expires_at')

    # The bus comes back empty: what it held was wiped on the way up.
    op.create_table(
        'gossip_milestones',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('source_user_id', sa.String(length=36), nullable=False),
        sa.Column('source_username', sa.String(length=64), nullable=False),
        sa.Column('reporting_agent_id', sa.String(length=36), nullable=True),
        sa.Column('reporting_agent_name', sa.String(length=128), nullable=False),
        sa.Column('target_scope', sa.String(length=64), nullable=False),
        sa.Column('category', sa.String(length=64), nullable=False),
        sa.Column('summary', sa.Text(), nullable=False),
        sa.Column('details_json', sa.JSON(), nullable=False),
        sa.Column('expires_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('source_session_id', sa.String(length=36), nullable=True),
        sa.Column('is_active', sa.Boolean(), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['reporting_agent_id'], ['agent_personalities.id'], ondelete='SET NULL'),
        sa.ForeignKeyConstraint(['source_session_id'], ['conversation_sessions.id'], ondelete='SET NULL'),
        sa.ForeignKeyConstraint(['source_user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
    )
    with op.batch_alter_table('gossip_milestones', schema=None) as batch_op:
        batch_op.create_index(batch_op.f('ix_gossip_milestones_category'), ['category'], unique=False)
        batch_op.create_index(batch_op.f('ix_gossip_milestones_expires_at'), ['expires_at'], unique=False)
        batch_op.create_index(batch_op.f('ix_gossip_milestones_is_active'), ['is_active'], unique=False)
        batch_op.create_index(batch_op.f('ix_gossip_milestones_reporting_agent_id'), ['reporting_agent_id'], unique=False)
        batch_op.create_index(batch_op.f('ix_gossip_milestones_source_user_id'), ['source_user_id'], unique=False)
        batch_op.create_index(batch_op.f('ix_gossip_milestones_target_scope'), ['target_scope'], unique=False)
