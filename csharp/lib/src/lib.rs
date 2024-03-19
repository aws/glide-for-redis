/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */
use glide_core::client;
use glide_core::client::Client as GlideClient;
use redis::{Cmd, FromRedisValue, RedisResult};
use std::{
    ffi::{c_void, CStr, CString},
    os::raw::c_char,
};
use tokio::runtime::Builder;
use tokio::runtime::Runtime;

pub enum Level {
    Error = 0,
    Warn = 1,
    Info = 2,
    Debug = 3,
    Trace = 4,
}

pub struct Client {
    client: GlideClient,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (), // TODO - add specific error codes
    runtime: Runtime,
}

fn create_connection_request(host: String, port: u32, use_tls: bool) -> client::ConnectionRequest {
    let address_info = client::NodeAddress {
        host,
        port: port as u16,
    };
    let addresses = vec![address_info];
    client::ConnectionRequest {
        addresses,
        tls_mode: if use_tls {
            Some(client::TlsMode::SecureTls)
        } else {
            Some(client::TlsMode::NoTls)
        },
        ..Default::default()
    }
}

fn create_client_internal(
    host: *const c_char,
    port: u32,
    use_tls: bool,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> RedisResult<Client> {
    let host_cstring = unsafe { CStr::from_ptr(host as *mut c_char) };
    let host_string = host_cstring.to_str()?.to_string();
    let request = create_connection_request(host_string, port, use_tls);
    let runtime = Builder::new_multi_thread()
        .enable_all()
        .thread_name("GLIDE for Redis C# thread")
        .build()?;
    let _runtime_handle = runtime.enter();
    let client = runtime.block_on(GlideClient::new(request)).unwrap(); // TODO - handle errors.
    Ok(Client {
        client,
        success_callback,
        failure_callback,
        runtime,
    })
}

/// Creates a new client to the given address. The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. All callbacks should be offloaded to separate threads in order not to exhaust the client's thread pool.
#[no_mangle]
pub extern "C" fn create_client(
    host: *const c_char,
    port: u32,
    use_tls: bool,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> *const c_void {
    match create_client_internal(host, port, use_tls, success_callback, failure_callback) {
        Err(_) => std::ptr::null(), // TODO - log errors
        Ok(client) => Box::into_raw(Box::new(client)) as *const c_void,
    }
}

#[no_mangle]
pub extern "C" fn close_client(client_ptr: *const c_void) {
    let client_ptr = unsafe { Box::from_raw(client_ptr as *mut Client) };
    let _runtime_handle = client_ptr.runtime.enter();
    drop(client_ptr);
}

/// Expects that key and value will be kept valid until the callback is called.
#[no_mangle]
pub extern "C" fn set(
    client_ptr: *const c_void,
    callback_index: usize,
    key: *const c_char,
    value: *const c_char,
) {
    let client = unsafe { Box::leak(Box::from_raw(client_ptr as *mut Client)) };
    // The safety of this needs to be ensured by the calling code. Cannot dispose of the pointer before all operations have completed.
    let ptr_address = client_ptr as usize;

    let key_cstring = unsafe { CStr::from_ptr(key as *mut c_char) };
    let value_cstring = unsafe { CStr::from_ptr(value as *mut c_char) };
    let mut client_clone = client.client.clone();
    client.runtime.spawn(async move {
        let key_bytes = key_cstring.to_bytes();
        let value_bytes = value_cstring.to_bytes();
        let mut cmd = Cmd::new();
        cmd.arg("SET").arg(key_bytes).arg(value_bytes);
        let result = client_clone.send_command(&cmd, None).await;
        unsafe {
            let client = Box::leak(Box::from_raw(ptr_address as *mut Client));
            match result {
                Ok(_) => (client.success_callback)(callback_index, std::ptr::null()), // TODO - should return "OK" string.
                Err(_) => (client.failure_callback)(callback_index), // TODO - report errors
            };
        }
    });
}

/// Expects that key will be kept valid until the callback is called. If the callback is called with a string pointer, the pointer must
/// be used synchronously, because the string will be dropped after the callback.
#[no_mangle]
pub extern "C" fn get(client_ptr: *const c_void, callback_index: usize, key: *const c_char) {
    let client = unsafe { Box::leak(Box::from_raw(client_ptr as *mut Client)) };
    // The safety of this needs to be ensured by the calling code. Cannot dispose of the pointer before all operations have completed.
    let ptr_address = client_ptr as usize;

    let key_cstring = unsafe { CStr::from_ptr(key as *mut c_char) };
    let mut client_clone = client.client.clone();
    client.runtime.spawn(async move {
        let key_bytes = key_cstring.to_bytes();
        let mut cmd = Cmd::new();
        cmd.arg("GET").arg(key_bytes);
        let result = client_clone.send_command(&cmd, None).await;
        let client = unsafe { Box::leak(Box::from_raw(ptr_address as *mut Client)) };
        let value = match result {
            Ok(value) => value,
            Err(_) => {
                unsafe { (client.failure_callback)(callback_index) }; // TODO - report errors,
                return;
            }
        };
        let result = Option::<CString>::from_owned_redis_value(value);

        unsafe {
            match result {
                Ok(None) => (client.success_callback)(callback_index, std::ptr::null()),
                Ok(Some(c_str)) => (client.success_callback)(callback_index, c_str.as_ptr()),
                Err(_) => (client.failure_callback)(callback_index), // TODO - report errors
            };
        }
    });
}

impl From<logger_core::Level> for Level {
    fn from(level: logger_core::Level) -> Self {
        match level {
            logger_core::Level::Error => Level::Error,
            logger_core::Level::Warn => Level::Warn,
            logger_core::Level::Info => Level::Info,
            logger_core::Level::Debug => Level::Debug,
            logger_core::Level::Trace => Level::Trace,
        }
    }
}

impl From<Level> for logger_core::Level {
    fn from(level: Level) -> logger_core::Level {
        match level {
            Level::Error => logger_core::Level::Error,
            Level::Warn => logger_core::Level::Warn,
            Level::Info => logger_core::Level::Info,
            Level::Debug => logger_core::Level::Debug,
            Level::Trace => logger_core::Level::Trace,
        }
    }
}

#[no_mangle]
#[allow(improper_ctypes_definitions)]
/// # Safety
/// Unsafe function because creating string from pointer
pub unsafe extern "C" fn log(
    log_level: Level,
    log_identifier: *const c_char,
    message: *const c_char,
) {
    unsafe {
        logger_core::log(
            log_level.into(),
            CStr::from_ptr(log_identifier)
                .to_str()
                .expect("Can not read log_identifier argument."),
            CStr::from_ptr(message)
                .to_str()
                .expect("Can not read message argument."),
        );
    }
}

#[no_mangle]
#[allow(improper_ctypes_definitions)]
/// # Safety
/// Unsafe function because creating string from pointer
pub unsafe extern "C" fn init(level: Option<Level>, file_name: *const c_char) -> Level {
    let file_name_as_str;
    unsafe {
        file_name_as_str = if file_name.is_null() {
            None
        } else {
            Some(
                CStr::from_ptr(file_name)
                    .to_str()
                    .expect("Can not read string argument."),
            )
        };

        let logger_level = logger_core::init(level.map(|level| level.into()), file_name_as_str);
        logger_level.into()
    }
}
