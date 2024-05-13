/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.logging;

import lombok.Getter;
import lombok.NonNull;

public class Logger {
    // TODO: consider lazy loading the glide_rs library
    static {
        System.loadLibrary("glide_rs");
    }

    // Enum ordinal is used, so order of variants must be kept the same
    @Getter
    public enum Level {
        DISABLED(-1),
        ERROR(0),
        WARN(1),
        INFO(2),
        DEBUG(3),
        TRACE(4);

        private final int level;

        private Level(int level) {
            this.level = level;
        }
    }

    private static Logger instance;
    private static Level loggerLevel;

    private Logger(@NonNull Level level, String fileName) {
        loggerLevel = Level.values()[initInternal(level.getLevel(), fileName)];
    }

    private Logger(String fileName) {
        this(Level.DISABLED, fileName);
    }

    private Logger(@NonNull Level level) {
        this(level, null);
    }

    private Logger() {
        this(Level.DISABLED, null);
    }

    public static void init(@NonNull Level level, String fileName) {
        if (instance == null) {
            instance = new Logger(level, fileName);
        }
    }

    public static void init(String fileName) {
        init(Level.DISABLED, fileName);
    }

    public static void init() {
        init(Level.DISABLED, null);
    }

    public static void init(@NonNull Level level) {
        init(level, null);
    }

    public static void log(
            @NonNull Level level, @NonNull String logIdentifier, @NonNull String message) {
        if (instance == null) {
            instance = new Logger(Level.DISABLED, null);
        }
        if (!(level.getLevel() <= loggerLevel.getLevel())) {
            return;
        }
        logInternal(level.getLevel(), logIdentifier, message);
    }

    public void setLoggerConfig(@NonNull Level level, String fileName) {
        instance = new Logger(level, fileName);
    }

    private static native int initInternal(int level, String fileName);

    private static native void logInternal(int level, String logIdentifier, String message);
}
