/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.logging;

import static glide.ffi.resolvers.LoggerResolver.initInternal;
import static glide.ffi.resolvers.LoggerResolver.logInternal;

import java.util.function.Supplier;
import lombok.Getter;
import lombok.NonNull;

/**
 * A singleton class that allows logging which is consistent with logs from the internal rust core.
 * The logger can be set up in 2 ways -
 *
 * <ol>
 *   <li>By calling <code>Logger.init</code>, which configures the logger only if it wasn't
 *       previously configured.
 *   <li>By calling <code>Logger.setLoggerConfig</code>, which replaces the existing configuration,
 *       and means that new logs will not be saved with the logs that were sent before the call.
 * </ol>
 *
 * If <code>setLoggerConfig</code> wasn't called, the first log attempt will initialize a new logger
 * with default configuration decided by Glide core.
 */
public final class Logger {
    @Getter
    public enum Level {
        DISABLED(-2),
        DEFAULT(-1),
        ERROR(0),
        WARN(1),
        INFO(2),
        DEBUG(3),
        TRACE(4);

        private final int level;

        Level(int level) {
            this.level = level;
        }

        public static Level fromInt(int i) {
            switch (i) {
                case 0:
                    return ERROR;
                case 1:
                    return WARN;
                case 2:
                    return INFO;
                case 3:
                    return DEBUG;
                case 4:
                    return TRACE;
                default:
                    return DEFAULT;
            }
        }
    }

    @Getter private static Level loggerLevel;

    private static void initLogger(@NonNull Level level, String fileName) {
        if (level == Level.DISABLED) {
            loggerLevel = level;
            return;
        }
        loggerLevel = Level.fromInt(initInternal(level.getLevel(), fileName));
    }

    /**
     * Initialize a logger if it wasn't initialized before - this method is meant to be used when
     * there is no intention to replace an existing logger. The logger will filter all logs with a
     * level lower than the given level. If given a <code>fileName</code> argument, will write the
     * logs to files postfixed with <code>fileName</code>. If <code>fileName</code> isn't provided,
     * the logs will be written to the console.
     *
     * @param level Set the logger level to one of <code>
     *     [DISABLED, DEFAULT, ERROR, WARN, INFO, DEBUG, TRACE]
     *     </code>. If log level isn't provided, the logger will be configured with default
     *     configuration decided by Glide core.
     * @param fileName If provided, the target of the logs will be the file mentioned. Otherwise, logs
     *     will be printed to the console.
     */
    public static void init(@NonNull Level level, String fileName) {
        if (loggerLevel == null) {
            initLogger(level, fileName);
        }
    }

    /**
     * Initialize a logger if it wasn't initialized before - this method is meant to be used when
     * there is no intention to replace an existing logger. The logger will filter all logs with a
     * level lower than the default level decided by Glide core. Given a <code>fileName</code>
     * argument, will write the logs to files postfixed with <code>fileName</code>.
     *
     * @param fileName The target of the logs will be the file mentioned.
     */
    public static void init(@NonNull String fileName) {
        init(Level.DEFAULT, fileName);
    }

    /**
     * Initialize a logger if it wasn't initialized before - this method is meant to be used when
     * there is no intention to replace an existing logger. The logger will filter all logs with a
     * level lower than the default level decided by Glide core. The logs will be written to stdout.
     */
    public static void init() {
        init(Level.DEFAULT, null);
    }

    /**
     * Initialize a logger if it wasn't initialized before - this method is meant to be used when
     * there is no intention to replace an existing logger. The logger will filter all logs with a
     * level lower than the given level. The logs will be written to stdout.
     *
     * @param level Set the logger level to one of <code>[DEFAULT, ERROR, WARN, INFO, DEBUG, TRACE]
     *     </code>. If log level isn't provided, the logger will be configured with default
     *     configuration decided by Glide core.
     */
    public static void init(@NonNull Level level) {
        init(level, null);
    }

    /**
     * Logs the provided message if the provided log level is lower than the logger level. This
     * overload takes a <code>Supplier</code> to lazily construct the message.
     *
     * @param level The log level of the provided message.
     * @param logIdentifier The log identifier should give the log a context.
     * @param messageSupplier The <code>Supplier</code> of the message to log.
     */
    public static void log(
            @NonNull Level level,
            @NonNull String logIdentifier,
            @NonNull Supplier<String> messageSupplier) {
        if (loggerLevel == null) {
            initLogger(Level.DEFAULT, null);
        }

        if (level == Level.DISABLED) {
            return;
        }

        if (!(level.getLevel() <= loggerLevel.getLevel())) {
            return;
        }
        logInternal(level.getLevel(), logIdentifier, messageSupplier.get());
    }

    /**
     * Logs the provided message if the provided log level is lower than the logger level.
     *
     * @param level The log level of the provided message.
     * @param logIdentifier The log identifier should give the log a context.
     * @param message The message to log.
     */
    public static void log(
            @NonNull Level level, @NonNull String logIdentifier, @NonNull String message) {
        if (loggerLevel == null) {
            initLogger(Level.DEFAULT, null);
        }

        if (level == Level.DISABLED) {
            return;
        }

        if (!(level.getLevel() <= loggerLevel.getLevel())) {
            return;
        }
        logInternal(level.getLevel(), logIdentifier, message);
    }

    /**
     * Creates a new logger instance and configure it with the provided log level and file name.
     *
     * @param level Set the logger level to one of <code>
     *     [DISABLED, DEFAULT, ERROR, WARN, INFO, DEBUG, TRACE]
     *     </code>. If log level isn't provided, the logger will be configured with default
     *     configuration decided by Glide core.
     * @param fileName If provided, the target of the logs will be the file mentioned. Otherwise, logs
     *     will be printed to stdout.
     */
    public static void setLoggerConfig(@NonNull Level level, String fileName) {
        initLogger(level, fileName);
    }

    /**
     * Creates a new logger instance and configure it with the provided log level. The logs will be
     * written to stdout.
     *
     * @param level Set the logger level to one of <code>
     *     [DISABLED, DEFAULT, ERROR, WARN, INFO, DEBUG, TRACE]
     *     </code>. If log level isn't provided, the logger will be configured with default
     *     configuration decided by Glide core.
     */
    public static void setLoggerConfig(@NonNull Level level) {
        setLoggerConfig(level, null);
    }

    /**
     * Creates a new logger instance and configure it with the provided file name and default log
     * level. The logger will filter all logs with a level lower than the default level decided by the
     * Glide core.
     *
     * @param fileName If provided, the target of the logs will be the file mentioned. Otherwise, logs
     *     will be printed to stdout.
     */
    public static void setLoggerConfig(String fileName) {
        setLoggerConfig(Level.DEFAULT, fileName);
    }

    /**
     * Creates a new logger instance. The logger will filter all logs with a level lower than the
     * default level decided by Glide core. The logs will be written to stdout.
     */
    public static void setLoggerConfig() {
        setLoggerConfig(Level.DEFAULT, null);
    }
}
