/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use jni::{errors::Error as JNIError, JNIEnv};
use log::error;
use std::string::FromUtf8Error;

pub enum FFIError {
    Jni(JNIError),
    Uds(String),
    Utf8(FromUtf8Error),
}

impl From<jni::errors::Error> for FFIError {
    fn from(value: jni::errors::Error) -> Self {
        FFIError::Jni(value)
    }
}

impl From<FromUtf8Error> for FFIError {
    fn from(value: FromUtf8Error) -> Self {
        FFIError::Utf8(value)
    }
}

impl std::fmt::Display for FFIError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FFIError::Jni(err) => write!(f, "{}", err),
            FFIError::Uds(err) => write!(f, "{}", err),
            FFIError::Utf8(err) => write!(f, "{}", err),
        }
    }
}

#[derive(Copy, Clone)]
pub enum ExceptionType {
    Exception,
    RuntimeException,
}

impl std::fmt::Display for ExceptionType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ExceptionType::Exception => write!(f, "java/lang/Exception"),
            ExceptionType::RuntimeException => write!(f, "java/lang/RuntimeException"),
        }
    }
}

// This handles `FFIError`s by converting them to Java exceptions and throwing them.
pub fn handle_errors<T>(env: &mut JNIEnv, result: Result<T, FFIError>) -> Option<T> {
    match result {
        Ok(value) => Some(value),
        Err(err) => {
            match err {
                FFIError::Utf8(utf8_error) => throw_java_exception(
                    env,
                    ExceptionType::RuntimeException,
                    &utf8_error.to_string(),
                ),
                error => throw_java_exception(env, ExceptionType::Exception, &error.to_string()),
            };
            // Return `None` because we need to still return a value after throwing.
            // This signals to the caller that we need to return the default value.
            None
        }
    }
}

// This function handles Rust panics by converting them into Java exceptions and throwing them.
// `func` returns an `Option<T>` because this is intended to wrap the output of `handle_errors`.
pub fn handle_panics<T, F: std::panic::UnwindSafe + FnOnce() -> Option<T>>(
    func: F,
    ffi_func_name: &str,
) -> Option<T> {
    match std::panic::catch_unwind(func) {
        Ok(value) => value,
        Err(_err) => {
            // Following https://github.com/jni-rs/jni-rs/issues/76#issuecomment-363523906
            // and throwing a runtime exception is not feasible here because of https://github.com/jni-rs/jni-rs/issues/432
            error!("Native function {} panicked.", ffi_func_name);
            None
        }
    }
}

pub fn throw_java_exception(env: &mut JNIEnv, exception_type: ExceptionType, message: &str) {
    match env.exception_check() {
        Ok(true) => (),
        Ok(false) => {
            env.throw_new(exception_type.to_string(), message)
                .unwrap_or_else(|err| {
                    error!(
                        "Failed to create exception with string {}: {}",
                        message,
                        err.to_string()
                    );
                });
        }
        Err(err) => {
            error!(
                "Failed to check if an exception is currently being thrown: {}",
                err.to_string()
            );
        }
    }
}
