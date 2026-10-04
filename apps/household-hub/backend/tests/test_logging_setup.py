"""The hub prints its own info lines, so what the log is told at that level can be read with docker logs."""

import logging

from app.bootstrap.app_factory import HUB_LOG_HANDLER, create_app


def _printers():
    return [handler for handler in logging.getLogger("app").handlers if handler.get_name() == HUB_LOG_HANDLER]


def test_GIVEN_the_app_is_created_WHEN_it_logs_at_info_THEN_the_line_has_a_handler_to_print_it():
    create_app()

    hub = logging.getLogger("app")
    assert hub.level == logging.INFO
    assert len(_printers()) == 1
    assert logging.getLogger("app.domain.use_cases.chat.process_chat_turn").isEnabledFor(logging.INFO)


def test_GIVEN_the_app_is_created_twice_WHEN_it_logs_THEN_each_line_is_printed_once():
    create_app()
    create_app()

    assert len(_printers()) == 1
