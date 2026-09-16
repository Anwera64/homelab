"""
The hub's own way back in. Whoever can reach the server can issue a reset code for any member —
the true backstop for a forgotten PIN, and the one that cannot be lost with a phone.

    python -m app.cli reset-pin "Emma"

It prints a code instead of setting a PIN, so no PIN ends up in a shell history, and the member
redeems it exactly as they would one read out by a housemate.
"""
import argparse
import asyncio
import sys
from typing import Optional, Sequence

from app.core.database import AsyncSessionLocal
from app.data.datasources.pin_reset_data_source import SqlitePinResetDataSource
from app.data.datasources.user_data_source import SqliteUserDataSource
from app.data.mappers.pin_reset_data_mapper import PinResetDataMapper
from app.data.mappers.user_data_mapper import UserDataMapper
from app.data.persistence.unit_of_work import SqliteUnitOfWork
from app.data.repositories.pin_reset_repository_impl import PinResetRepositoryImpl
from app.data.repositories.user_repository_impl import UserRepositoryImpl
from app.domain.entities.one_time_code import CODE_LIFETIME, new_code
from app.domain.entities.pin_reset import PinReset, get_utc_now

MINUTES = int(CODE_LIFETIME.total_seconds() // 60)


class MemberNotFoundError(Exception):
    """No active member goes by that name."""


async def issue_reset_code(full_name: str, session_factory=AsyncSessionLocal) -> str:
    """A reset code for an active member, with no approver: the hub itself vouched for them."""
    async with session_factory() as session:
        user_repo = UserRepositoryImpl(SqliteUserDataSource(session), UserDataMapper())
        reset_repo = PinResetRepositoryImpl(SqlitePinResetDataSource(session), PinResetDataMapper())
        uow = SqliteUnitOfWork(session)

        wanted = full_name.strip().casefold()
        member = next((m for m in await user_repo.list_active() if m.full_name.casefold() == wanted), None)
        if not member:
            raise MemberNotFoundError(full_name)

        now = get_utc_now()
        reset = PinReset(
            code=new_code(),
            target_user_id=member.id,
            expires_at=now + CODE_LIFETIME,
            created_at=now,
        )
        async with uow:
            await reset_repo.delete_unused_for_target(member.id)
            created = await reset_repo.create(reset)
            await uow.commit()
        return created.code


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="hub", description="Household Hub maintenance commands.")
    commands = parser.add_subparsers(dest="command", required=True)
    reset = commands.add_parser("reset-pin", help="Issue a reset code so a member can choose a new PIN.")
    reset.add_argument("name", help="The member's name, as the profile picker shows it.")
    return parser


async def main(argv: Optional[Sequence[str]] = None, session_factory=AsyncSessionLocal) -> int:
    args = _parser().parse_args(argv)
    if args.command == "reset-pin":
        try:
            code = await issue_reset_code(args.name, session_factory=session_factory)
        except MemberNotFoundError:
            print(f"Nobody in this household is called {args.name}.", file=sys.stderr)
            return 1
        print(f"Reset code for {args.name}: {code}")
        print(f"It works once, within {MINUTES} minutes. They enter it under \"I have a reset code\".")
        return 0
    return 1


def run() -> None:
    raise SystemExit(asyncio.run(main()))


if __name__ == "__main__":
    run()
