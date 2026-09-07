from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.models.agent import AgentPersonality


BUILTIN_AGENTS = [
    {
        "slug": "researcher",
        "name": "Researcher",
        "description": "Rigorous academic and document researcher. Specializes in deep technical synthesis, theory, citations, structured summaries, and document analysis.",
        "avatar": "🔬",
        "system_prompt": (
            "You are the Household Hub Researcher. You specialize in deep academic analysis, "
            "technical literature synthesis, architectural theory, citations, complex document "
            "summarization, and fact-grounded reasoning."
        ),
        "model_alias": "qwen3:14b",
        "temperature": 0.3,
        "top_p": 0.9,
        "tool_permissions": ["pdf_reader", "searxng_search", "document_writer"],
        "is_builtin": True,
        "is_active": True,
        "owner_id": None,
    },
    {
        "slug": "assistant",
        "name": "Assistant",
        "description": "General household coordinator and personal assistant. Helps with daily coordination, calendar planning, meal prep, and morning briefings.",
        "avatar": "🏡",
        "system_prompt": (
            "You are the Household Hub Assistant. You help household members coordinate schedules, "
            "plan meals, organize everyday logistics, and provide concise morning briefings."
        ),
        "model_alias": "qwen3:14b",
        "temperature": 0.7,
        "top_p": 0.9,
        "tool_permissions": ["calendar_read", "calendar_write", "searxng_search"],
        "is_builtin": True,
        "is_active": True,
        "owner_id": None,
    },
]


async def seed_builtin_agents(db: AsyncSession) -> None:
    """Ensure the 2 baseline built-in agent personalities exist in the catalog."""
    for agent_data in BUILTIN_AGENTS:
        result = await db.execute(
            select(AgentPersonality).where(AgentPersonality.slug == agent_data["slug"])
        )
        existing = result.scalars().first()
        if not existing:
            agent = AgentPersonality(**agent_data)
            db.add(agent)
    await db.flush()
