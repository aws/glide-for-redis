# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Optional


class RedisError(Exception):
    """
    Base class for errors.
    """

    def __init__(self, message: Optional[str] = None):
        super().__init__(message or "No error message provided")

    def name(self):
        return self.__class__.__name__


class ClosingError(RedisError):
    """
    Errors that report that the client has closed and is no longer usable.
    """

    pass


class RequestError(RedisError):
    """
    Errors that were reported during a request.
    """

    pass


class TimeoutError(RequestError):
    """
    Errors that are thrown when a request times out.
    """

    pass


class ExecAbortError(RequestError):
    """
    Errors that are thrown when a transaction is aborted.
    """

    pass


class ConnectionError(RequestError):
    """
    Errors that are thrown when a connection disconnects.
    These errors can be temporary, as the client will attempt to reconnect.
    """

    pass


class ConfigurationError(RequestError):
    """
    Errors that are thrown when a request cannot be completed in current configuration settings.
    """
