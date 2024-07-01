/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/// Base class for errors.
export abstract class RedisError extends Error {
    public message: string;

    constructor(message?: string) {
        super();
        this.message = message ?? "No error message provided";
    }

    public get name(): string {
        return this.constructor.name;
    }
}

/// Errors that report that the client has closed and is no longer usable.
export class ClosingError extends RedisError {}

/// Errors that were reported during a request.
export class RequestError extends RedisError {}

/// Errors that are thrown when a request times out.
export class TimeoutError extends RequestError {}

export const TIMEOUT_ERROR = new TimeoutError("Operation timed out");

/// Errors that are thrown when a transaction is aborted.
export class ExecAbortError extends RequestError {}

/// Errors that are thrown when a connection disconnects. These errors can be temporary, as the client will attempt to reconnect.
export class ConnectionError extends RequestError {}
