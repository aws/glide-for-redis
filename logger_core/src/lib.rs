/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use once_cell::sync::OnceCell;
use std::sync::RwLock;
use tracing::{self, event};
use tracing_appender::rolling::{RollingFileAppender, Rotation};
use tracing_subscriber::{
    filter::Filtered,
    fmt::{
        format::{DefaultFields, Format},
        Layer,
    },
    layer::Layered,
    Registry,
};

use tracing_subscriber::{
    self,
    filter::{self, LevelFilter},
    prelude::*,
    reload::{self, Handle},
};
// Layer-Filter pair determines whether a log will be collected
type InnerFiltered = Filtered<Layer<Registry>, LevelFilter, Registry>;
// A Reloadable pair of layer-filter
type InnerLayered = Layered<reload::Layer<InnerFiltered, Registry>, Registry>;
// A reloadable layer of subscriber to a rolling file
type FileReload = Handle<
    Filtered<
        Layer<InnerLayered, DefaultFields, Format, RollingFileAppender>,
        LevelFilter,
        InnerLayered,
    >,
    InnerLayered,
>;

pub struct Reloads {
    console_reload: RwLock<reload::Handle<InnerFiltered, Registry>>,
    file_reload: RwLock<FileReload>,
}

pub struct InitiateOnce {
    init_once: OnceCell<Reloads>,
}

pub static INITIATE_ONCE: InitiateOnce = InitiateOnce {
    init_once: OnceCell::new(),
};

#[derive(Debug)]
pub enum Level {
    Error = 0,
    Warn = 1,
    Info = 2,
    Debug = 3,
    Trace = 4,
}
impl Level {
    fn to_filter(&self) -> filter::LevelFilter {
        match self {
            Level::Trace => LevelFilter::TRACE,
            Level::Debug => LevelFilter::DEBUG,
            Level::Info => LevelFilter::INFO,
            Level::Warn => LevelFilter::WARN,
            Level::Error => LevelFilter::ERROR,
        }
    }
}

// Initialize the global logger to error level on the first call only
// In any of the calls to the function, including the first - resetting the existence loggers to the new setting
// provided by using the global reloadable handle
// The logger will save only logs of the given level or above.
pub fn init(minimal_level: Option<Level>, file_name: Option<&str>) -> Level {
    let level = minimal_level.unwrap_or(Level::Warn);
    let level_filter = level.to_filter();
    let reloads = INITIATE_ONCE.init_once.get_or_init(|| {
        let stdout_fmt = tracing_subscriber::fmt::layer()
            .with_ansi(true)
            .with_filter(LevelFilter::OFF);

        let (stdout_layer, stdout_reload) = reload::Layer::new(stdout_fmt);

        let file_appender = RollingFileAppender::new(
            Rotation::HOURLY,
            "glide-logs",
            file_name.unwrap_or("output.log"),
        );
        let file_fmt = tracing_subscriber::fmt::layer()
            .with_writer(file_appender)
            .with_filter(LevelFilter::OFF);
        let (file_layer, file_reload) = reload::Layer::new(file_fmt);

        // Enable logging only from allowed crates
        let targets_filter = filter::Targets::new()
            .with_target("glide", LevelFilter::TRACE)
            .with_target("redis", LevelFilter::TRACE)
            .with_target("logger_core", LevelFilter::TRACE)
            .with_target(std::env!("CARGO_PKG_NAME"), LevelFilter::TRACE);

        tracing_subscriber::registry()
            .with(stdout_layer)
            .with(file_layer)
            .with(targets_filter)
            .init();

        let reloads: Reloads = Reloads {
            console_reload: RwLock::new(stdout_reload),
            file_reload: RwLock::new(file_reload),
        };
        reloads
    });

    match file_name {
        None => {
            let _ = reloads
                .console_reload
                .write()
                .expect("error reloading stdout")
                .modify(|layer| (*layer.filter_mut() = level_filter));
            let _ = reloads
                .file_reload
                .write()
                .expect("error reloading file appender")
                .modify(|layer| {
                    *layer.filter_mut() = LevelFilter::OFF;
                });
        }
        Some(file) => {
            let file_appender = RollingFileAppender::new(Rotation::HOURLY, "glide-logs", file);
            let _ = reloads
                .file_reload
                .write()
                .expect("error reloading file appender")
                .modify(|layer| {
                    *layer.filter_mut() = level_filter;
                    *layer.inner_mut().writer_mut() = file_appender;
                });
            let _ = reloads
                .console_reload
                .write()
                .expect("error reloading stdout")
                .modify(|layer| (*layer.filter_mut() = LevelFilter::OFF));
        }
    };
    level
}

macro_rules! create_log {
    ($name:ident, $uppercase_level:tt) => {
        pub fn $name<Message: AsRef<str>, Identifier: AsRef<str>>(
            log_identifier: Identifier,
            message: Message,
        ) {
            if INITIATE_ONCE.init_once.get().is_none() {
                init(Some(Level::Warn), None);
            };
            let message_ref = message.as_ref();
            let identifier_ref = log_identifier.as_ref();
            event!(
                tracing::Level::$uppercase_level,
                "{identifier_ref} - {message_ref}"
            )
        }
    };
}

create_log!(log_trace, TRACE);
create_log!(log_debug, DEBUG);
create_log!(log_info, INFO);
create_log!(log_warn, WARN);
create_log!(log_error, ERROR);

// Logs the given log, with log_identifier and log level prefixed. If the given log level is below the threshold of given when the logger was initialized, the log will be ignored.
// log_identifier should be used to add context to a log, and make it easier to connect it to other relevant logs. For example, it can be used to pass a task identifier.
// If this is called before a logger was initialized the log will not be registered.
// If logger doesn't exist, create the default
pub fn log<Message: AsRef<str>, Identifier: AsRef<str>>(
    log_level: Level,
    log_identifier: Identifier,
    message: Message,
) {
    match log_level {
        Level::Debug => log_debug(log_identifier, message),
        Level::Trace => log_trace(log_identifier, message),
        Level::Info => log_info(log_identifier, message),
        Level::Warn => log_warn(log_identifier, message),
        Level::Error => log_error(log_identifier, message),
    }
}
