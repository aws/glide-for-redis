/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { InitInternalLogger, Level, log } from "glide-rs";

const LEVEL: Map<LevelOptions | undefined, Level | undefined> = new Map([
    ["error", Level.Error],
    ["warn", Level.Warn],
    ["info", Level.Info],
    ["debug", Level.Debug],
    ["trace", Level.Trace],
    [undefined, undefined],
]);
type LevelOptions = "error" | "warn" | "info" | "debug" | "trace";

/*
 * A singleton class that allows logging which is consistent with logs from the internal rust core.
 * The logger can be set up in 2 ways -
 *    1. By calling init, which configures the logger only if it wasn't previously configured.
 *     2. By calling Logger.setLoggerConfig, which replaces the existing configuration, and means that new logs will not be saved with the logs that were sent before the call. The previous logs will remain unchanged.
 * If no call to any of these function is received, the first log attempt will configure the logger with default configuration decided by rust core.
 */
export class Logger {
    private static _instance: Logger;
    private static logger_level = 0;

    private constructor(level?: LevelOptions, fileName?: string) {
        Logger.logger_level = InitInternalLogger(LEVEL.get(level), fileName);
    }

    /**
     * take the arguments from the user and provide to the core-logger (see ../logger-core)
     * if the level is higher then the logger level (error is 0, warn 1, etc.) simply return without operation
     * if a logger instance doesn't exist, create new one with default mode (decided by rust core, normally - level: error, target: console)
     * logIdentifier arg is a string contain data that suppose to give the log a context and make it easier to find certain type of logs.
     * when the log is connect to certain task the identifier should be the task id, when the log is not part of specific task the identifier should give a context to the log - for example, "socket connection".
     * External users shouldn't use this function.
     */
    public static log(
        logLevel: LevelOptions,
        logIdentifier: string,
        message: string,
    ) {
        if (!Logger._instance) {
            new Logger();
        }

        const level = LEVEL.get(logLevel) || 0;
        if (!(level <= Logger.logger_level)) return;
        log(level, logIdentifier, message);
    }

    /**
     * Initialize a logger if it wasn't initialized before - this method is meant to be used when there is no intention to replace an existing logger.
     * The logger will filter all logs with a level lower than the given level,
     * If given a fileName argument, will write the logs to files postfixed with fileName. If fileName isn't provided, the logs will be written to the console.
     */
    public static init(level?: LevelOptions, fileName?: string) {
        if (!this._instance) {
            this._instance = new this(level, fileName);
        }
    }

    /**
     * configure the logger.
     * the level argument is the level of the logs you want the system to provide (error logs, warn logs, etc.)
     * the filename argument is optional - if provided the target of the logs will be the file mentioned, else will be the console
     */
    public static setLoggerConfig(level: LevelOptions, fileName?: string) {
        this._instance = new this(level, fileName);
    }
}
