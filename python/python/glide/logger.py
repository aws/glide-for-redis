# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from enum import Enum
from typing import Optional

from .glide import Level as internalLevel
from .glide import py_init, py_log


class Level(Enum):
    ERROR = internalLevel.Error
    WARN = internalLevel.Warn
    INFO = internalLevel.Info
    DEBUG = internalLevel.Debug
    TRACE = internalLevel.Trace


class Logger:
    """
    A singleton class that allows logging which is consistent with logs from the internal rust core.
    The logger can be set up in 2 ways -
    1. By calling Logger.init, which configures the logger only if it wasn't previously configured.
    2. By calling Logger.set_logger_config, which replaces the existing configuration, and means that new logs will not be
        saved with the logs that were sent before the call.
    If set_logger_config wasn't called, the first log attempt will initialize a new logger with default configuration decided
    by the Rust core.
    """

    _instance = None
    logger_level: internalLevel

    def __init__(self, level: Optional[Level] = None, file_name: Optional[str] = None):
        level_value = level.value if level else None
        Logger.logger_level = py_init(level_value, file_name)

    @classmethod
    def init(cls, level: Optional[Level] = None, file_name: Optional[str] = None):
        """_summary_
        Initialize a logger if it wasn't initialized before - this method is meant to be used when there is no intention to
        replace an existing logger.
        The logger will filter all logs with a level lower than the given level,
        If given a fileName argument, will write the logs to files postfixed with fileName. If fileName isn't provided,
        the logs will be written to the console.
        Args:
            level (Optional[Level]): Set the logger level to one of [ERROR, WARN, INFO, DEBUG, TRACE].
            If log level isn't provided, the logger will be configured with default configuration decided by the Rust core.
            file_name (Optional[str]):  If providedv the target of the logs will be the file mentioned.
            Otherwise, logs will be printed to the console.
        """
        if cls._instance is None:
            cls._instance = cls(level, file_name)

    @classmethod
    def log(cls, log_level: Level, log_identifier: str, message: str):
        """Logs the provided message if the provided log level is lower then the logger level.

        Args:
            log_level (Level): The log level of the provided message
            log_identifier (str): The log identifier should give the log a context.
            message (str): The message to log.
        """
        if not cls._instance:
            cls._instance = cls(None)
        if not log_level.value.is_lower(Logger.logger_level):
            return
        py_log(log_level.value, log_identifier, message)

    @classmethod
    def set_logger_config(
        cls, level: Optional[Level] = None, file_name: Optional[str] = None
    ):
        """Creates a new logger instance and configure it with the provided log level and file name.

        Args:
            level (Optional[Level]): Set the logger level to one of [ERROR, WARN, INFO, DEBUG, TRACE].
            If log level isn't provided, the logger will be configured with default configuration decided by the Rust core.
            file_name (Optional[str]):  If providedv the target of the logs will be the file mentioned.
            Otherwise, logs will be printed to the console.
        """
        Logger._instance = Logger(level, file_name)
