using System.Runtime.InteropServices;
using System.Text;


namespace babushka
{

    public enum Level
    {
        Error = 0,
        Warn = 1,
        Info = 2,
        Debug = 3,
        Trace = 4
    }

    /*
    A class that allows logging which is consistent with logs from the internal rust core.
    Only one instance of this class can exist at any given time. The logger can be set up in 2 ways -
        1. By calling init, which creates and modifies a new logger only if one doesn't exist.
        2. By calling setConfig, which replaces the existing logger, and means that new logs will not be saved with the logs that were sent before the call.
    If no call to any of these function is received, the first log attempt will initialize a new logger with default level decided by rust core (normally - console, error).
    External users shouldn't user Logger, and instead setLoggerConfig Before starting to use the client.
    */
    public class Logger
    {
        #region private fields

        private static Logger? _instance = null;
        private static Level? loggerLevel = null;
        #endregion private fields

        #region private methods
        private Logger(Level? level, string? filename)
        {
            var buffer = filename is null ? null : Encoding.UTF8.GetBytes(filename);
            Logger.loggerLevel = InitInternalLogger(Convert.ToInt32(level), buffer);
        }
        #endregion private methods

        #region internal methods
        // Initialize a logger instance if none were initialized before - this method is meant to be used when there is no intention to replace an existing logger.
        // The logger will filter all logs with a level lower than the given level,
        // If given a fileName argument, will write the logs to files postfixed with fileName. If fileName isn't provided, the logs will be written to the console.
        internal static Logger Init(Level? level, string? filename)
        {
            if (Logger._instance is null)
            {
                Logger._instance = new Logger(level, filename);
            }
            return Logger._instance;
        }

        // take the arguments from the user and provide to the core-logger (see ../logger-core)
        // if the level is higher then the logger level (error is 0, warn 1, etc.) simply return without operation
        // if a logger instance doesn't exist, create new one with default mode (decided by rust core, normally - level: error, target: console)
        // logIdentifier arg is a string contain data that suppose to give the log a context and make it easier to find certain type of logs.
        // when the log is connect to certain task the identifier should be the task id, when the log is not part of specific task the identifier should give a context to the log - for example, "socket connection".
        internal static void Log(Level logLevel, string logIdentifier, string message)
        {
            if (Logger._instance == null)
            {
                Logger._instance = new Logger(null, null);
            }
            if (!(logLevel <= Logger.loggerLevel)) return;
            log(Convert.ToInt32(logLevel), Encoding.UTF8.GetBytes(logIdentifier), Encoding.UTF8.GetBytes(message));
        }
        #endregion internal methods

        #region public methods
        // config the logger instance - in fact - create new logger instance with the new args
        // exist in addition to init for two main reason's:
        // 1. if Babushka dev want intentionally to change the logger instance configuration
        // 2. external user want to set the logger and we don't want to return to him the logger itself, just config it
        // the level argument is the level of the logs you want the system to provide (error logs, warn logs, etc.)
        // the filename argument is optional - if provided the target of the logs will be the file mentioned, else will be the console
        public static void SetConfig(Level? level, string? fileName)
        {
            Logger._instance = new Logger(level, fileName);
        }
        #endregion public methods

        #region FFI function declaration
        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "log")]
        private static extern void log(Int32 logLevel, byte[] logIdentifier, byte[] message);

        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "init")]
        private static extern Level InitInternalLogger(Int32 level, byte[]? filename);

        #endregion
    }


}
