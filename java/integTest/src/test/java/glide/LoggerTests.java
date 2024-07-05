/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.logging.Logger;
import java.io.File;
import java.util.Scanner;
import lombok.SneakyThrows;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

public class LoggerTests {

    private final Logger.Level DEFAULT_TEST_LOG_LEVEL = Logger.Level.WARN;

    @Test
    public void init_logger() {
        Logger.init(DEFAULT_TEST_LOG_LEVEL);
        assertEquals(DEFAULT_TEST_LOG_LEVEL, Logger.getLoggerLevel());
        // The logger is already configured, so calling init again shouldn't modify the log level
        Logger.init(Logger.Level.ERROR);
        assertEquals(DEFAULT_TEST_LOG_LEVEL, Logger.getLoggerLevel());
    }

    @Test
    public void set_logger_config() {
        Logger.setLoggerConfig(Logger.Level.INFO);
        assertEquals(Logger.Level.INFO, Logger.getLoggerLevel());
        // Revert to the default test log level
        Logger.setLoggerConfig(DEFAULT_TEST_LOG_LEVEL);
        assertEquals(DEFAULT_TEST_LOG_LEVEL, Logger.getLoggerLevel());
    }

    @SneakyThrows
    @RepeatedTest(1000)
    public void log_to_file() {
        String infoIdentifier = "Info";
        String infoMessage = "foo";
        String warnIdentifier = "Warn";
        String warnMessage = "woof";
        String errorIdentifier = "Error";
        String errorMessage = "meow";
        String debugIdentifier = "Debug";
        String debugMessage = "chirp";
        String traceIdentifier = "Trace";
        String traceMessage = "squawk";

        Logger.setLoggerConfig(Logger.Level.INFO, "log.txt");
        Logger.log(Logger.Level.INFO, infoIdentifier, infoMessage);
        Logger.log(Logger.Level.WARN, warnIdentifier, warnMessage);
        Logger.log(Logger.Level.ERROR, errorIdentifier, errorMessage);
        Logger.log(Logger.Level.DEBUG, debugIdentifier, debugMessage);
        Logger.log(Logger.Level.TRACE, traceIdentifier, traceMessage);

        // Test logging with lazily constructed messages
        Logger.log(Logger.Level.INFO, infoIdentifier, () -> infoMessage);
        Logger.log(Logger.Level.WARN, warnIdentifier, () -> warnMessage);
        Logger.log(Logger.Level.ERROR, errorIdentifier, () -> errorMessage);
        Logger.log(Logger.Level.DEBUG, debugIdentifier, () -> debugMessage);
        Logger.log(Logger.Level.TRACE, traceIdentifier, () -> traceMessage);

        // Initialize a new logger to force closing of existing files
        Logger.setLoggerConfig(Logger.Level.DEFAULT, "dummy.txt");

        File logFolder = new File("glide-logs");
        File[] logFiles = logFolder.listFiles((dir, name) -> name.startsWith("log.txt."));
        assertNotNull(logFiles);
        File logFile = logFiles[0];
        logFile.deleteOnExit();
        Scanner reader = new Scanner(logFile);
        String infoLine = reader.nextLine();
        String warnLine = reader.nextLine();
        String errorLine = reader.nextLine();
        String infoLineLazy = reader.nextLine();
        String warnLineLazy = reader.nextLine();
        String errorLineLazy = reader.nextLine();
        assertFalse(reader.hasNextLine());
        reader.close();

        assertTrue(infoLine.contains(infoIdentifier + " - " + infoMessage));
        assertTrue(warnLine.contains(warnIdentifier + " - " + warnMessage));
        assertTrue(errorLine.contains(errorIdentifier + " - " + errorMessage));
        assertTrue(infoLineLazy.contains(infoIdentifier + " - " + infoMessage));
        assertTrue(warnLineLazy.contains(warnIdentifier + " - " + warnMessage));
        assertTrue(errorLineLazy.contains(errorIdentifier + " - " + errorMessage));
    }
}
