/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

#[cfg(not(target_env = "msvc"))]
use tikv_jemallocator::Jemalloc;

#[cfg(not(target_env = "msvc"))]
#[global_allocator]
static GLOBAL: Jemalloc = Jemalloc;

use byteorder::{LittleEndian, WriteBytesExt};
use bytes::Bytes;
use glide_core::start_socket_listener;
use glide_core::MAX_REQUEST_ARGS_LENGTH;
#[cfg(feature = "testing_utilities")]
use napi::bindgen_prelude::BigInt;
use napi::bindgen_prelude::Uint8Array;
use napi::{Env, Error, JsObject, JsUnknown, Result, Status};
use napi_derive::napi;
use num_traits::sign::Signed;
use redis::{aio::MultiplexedConnection, AsyncCommands, FromRedisValue, Value};
#[cfg(feature = "testing_utilities")]
use std::collections::HashMap;
use std::str;
use tokio::runtime::{Builder, Runtime};
#[napi]
pub enum Level {
    Debug = 3,
    Error = 0,
    Info = 2,
    Trace = 4,
    Warn = 1,
}

#[napi]
pub const MAX_REQUEST_ARGS_LEN: u32 = MAX_REQUEST_ARGS_LENGTH as u32;

#[napi]
pub const DEFAULT_TIMEOUT_IN_MILLISECONDS: u32 =
    glide_core::client::DEFAULT_RESPONSE_TIMEOUT.as_millis() as u32;

#[napi]
struct AsyncClient {
    #[allow(dead_code)]
    connection: MultiplexedConnection,
    runtime: Runtime,
}

fn to_js_error(err: impl std::error::Error) -> Error {
    napi::Error::new(Status::Unknown, err.to_string())
}

fn to_js_result<T, E: std::error::Error>(result: std::result::Result<T, E>) -> Result<T> {
    result.map_err(to_js_error)
}

#[napi]
impl AsyncClient {
    #[napi(js_name = "CreateConnection")]
    #[allow(dead_code)]
    pub fn create_connection(connection_address: String) -> Result<AsyncClient> {
        let runtime = Builder::new_multi_thread()
            .enable_all()
            .worker_threads(1)
            .thread_name("GLIDE node thread")
            .build()?;
        let _runtime_handle = runtime.enter();
        let client = to_js_result(redis::Client::open(connection_address))?;
        let connection =
            to_js_result(runtime.block_on(client.get_multiplexed_async_connection(None)))?;
        Ok(AsyncClient {
            connection,
            runtime,
        })
    }

    #[napi(ts_return_type = "Promise<string | null>")]
    #[allow(dead_code)]
    pub fn get(&self, env: Env, key: String) -> Result<JsObject> {
        let (deferred, promise) = env.create_deferred()?;

        let mut connection = self.connection.clone();
        self.runtime.spawn(async move {
            let result: Result<Option<String>> = to_js_result(connection.get(key).await);
            match result {
                Ok(value) => deferred.resolve(|_| Ok(value)),
                Err(e) => deferred.reject(e),
            }
        });

        Ok(promise)
    }

    #[napi(ts_return_type = "Promise<string | \"OK\" | null>")]
    #[allow(dead_code)]
    pub fn set(&self, env: Env, key: String, value: String) -> Result<JsObject> {
        let (deferred, promise) = env.create_deferred()?;

        let mut connection = self.connection.clone();
        self.runtime.spawn(async move {
            let result: Result<()> = to_js_result(connection.set(key, value).await);
            match result {
                Ok(_) => deferred.resolve(|_| Ok("OK")),
                Err(e) => deferred.reject(e),
            }
        });

        Ok(promise)
    }
}

#[napi(js_name = "StartSocketConnection", ts_return_type = "Promise<string>")]
pub fn start_socket_listener_external(env: Env) -> Result<JsObject> {
    let (deferred, promise) = env.create_deferred()?;

    start_socket_listener(move |result| {
        match result {
            Ok(path) => deferred.resolve(|_| Ok(path)),
            Err(error_message) => deferred.reject(napi::Error::new(Status::Unknown, error_message)),
        };
    });

    Ok(promise)
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

#[napi]
pub fn log(log_level: Level, log_identifier: String, message: String) {
    logger_core::log(log_level.into(), log_identifier, message);
}

#[napi(js_name = "InitInternalLogger")]
pub fn init(level: Option<Level>, file_name: Option<&str>) -> Level {
    let logger_level = logger_core::init(level.map(|level| level.into()), file_name);
    logger_level.into()
}

fn redis_value_to_js(val: Value, js_env: Env) -> Result<JsUnknown> {
    match val {
        Value::Nil => js_env.get_null().map(|val| val.into_unknown()),
        Value::SimpleString(str) => js_env
            .create_string_from_std(str)
            .map(|val| val.into_unknown()),
        Value::Okay => js_env.create_string("OK").map(|val| val.into_unknown()),
        Value::Int(num) => js_env.create_int64(num).map(|val| val.into_unknown()),
        Value::BulkString(data) => Ok(js_env.create_buffer_with_data(data)?.into_unknown()),
        Value::Array(array) => {
            let mut js_array_view = js_env.create_array_with_length(array.len())?;
            for (index, item) in array.into_iter().enumerate() {
                js_array_view.set_element(index as u32, redis_value_to_js(item, js_env)?)?;
            }
            Ok(js_array_view.into_unknown())
        }
        Value::Map(map) => {
            let mut obj = js_env.create_object()?;
            for (key, value) in map {
                let field_name = String::from_owned_redis_value(key).map_err(to_js_error)?;
                let value = redis_value_to_js(value, js_env)?;
                obj.set_named_property(&field_name, value)?;
            }
            Ok(obj.into_unknown())
        }
        Value::Double(float) => js_env.create_double(float).map(|val| val.into_unknown()),
        Value::Boolean(bool) => js_env.get_boolean(bool).map(|val| val.into_unknown()),
        // format is ignored, as per the RESP3 recommendations -
        // "Normal client libraries may ignore completely the difference between this"
        // "type and the String type, and return a string in both cases.""
        // https://github.com/redis/redis-specifications/blob/master/protocol/RESP3.md
        Value::VerbatimString { format: _, text } => {
            // VerbatimString is binary safe -> convert it into such
            Ok(js_env
                .create_buffer_with_data(text.as_bytes().to_vec())?
                .into_unknown())
        }
        Value::BigNumber(num) => {
            let sign = num.is_negative();
            let words = num.iter_u64_digits().collect();
            js_env
                .create_bigint_from_words(sign, words)
                .and_then(|val| val.into_unknown())
        }
        Value::Set(array) => {
            // TODO - return a set object instead of an array object
            let mut js_array_view = js_env.create_array_with_length(array.len())?;
            for (index, item) in array.into_iter().enumerate() {
                js_array_view.set_element(index as u32, redis_value_to_js(item, js_env)?)?;
            }
            Ok(js_array_view.into_unknown())
        }
        Value::Attribute { data, attributes } => {
            let mut obj = js_env.create_object()?;
            let value = redis_value_to_js(*data, js_env)?;
            obj.set_named_property("value", value)?;

            let value = redis_value_to_js(Value::Map(attributes), js_env)?;
            obj.set_named_property("attributes", value)?;

            Ok(obj.into_unknown())
        }
        Value::Push { kind: _, data: _ } => todo!(),
    }
}

#[napi(
    ts_return_type = "null | string | Uint8Array | number | {} | Boolean | BigInt | Set<any> | any[]"
)]
pub fn value_from_split_pointer(js_env: Env, high_bits: u32, low_bits: u32) -> Result<JsUnknown> {
    let mut bytes = [0_u8; 8];
    (&mut bytes[..4])
        .write_u32::<LittleEndian>(low_bits)
        .unwrap();
    (&mut bytes[4..])
        .write_u32::<LittleEndian>(high_bits)
        .unwrap();
    let pointer = u64::from_le_bytes(bytes);
    let value = unsafe { Box::from_raw(pointer as *mut Value) };
    redis_value_to_js(*value, js_env)
}

// Pointers are split because JS cannot represent a full usize using its `number` object.
// The pointer is split into 2 `number`s, and then combined back in `value_from_split_pointer`.
fn split_pointer<T>(pointer: *mut T) -> [u32; 2] {
    let pointer = pointer as usize;
    let bytes = usize::to_le_bytes(pointer);
    let [lower, higher] = unsafe { std::mem::transmute::<[u8; 8], [u32; 2]>(bytes) };
    [lower, higher]
}

#[napi(ts_return_type = "[number, number]")]
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_string(message: String) -> [u32; 2] {
    let value = Value::SimpleString(message);
    let pointer = Box::leak(Box::new(value)) as *mut Value;
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
pub fn create_leaked_string_vec(message: Vec<Uint8Array>) -> [u32; 2] {
    // Convert the string vec -> Bytes vector
    let bytes_vec: Vec<Bytes> = message.iter().map(|v| Bytes::from(v.to_vec())).collect();
    let pointer = Box::leak(Box::new(bytes_vec)) as *mut Vec<Bytes>;
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_map(map: HashMap<String, String>) -> [u32; 2] {
    let pointer = Box::leak(Box::new(Value::Map(
        map.into_iter()
            .map(|(key, value)| (Value::SimpleString(key), Value::SimpleString(value)))
            .collect(),
    ))) as *mut Value;
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_array(array: Vec<String>) -> [u32; 2] {
    let pointer = Box::leak(Box::new(Value::Array(
        array.into_iter().map(Value::SimpleString).collect(),
    ))) as *mut Value;
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_attribute(message: String, attribute: HashMap<String, String>) -> [u32; 2] {
    let pointer = Box::leak(Box::new(Value::Attribute {
        data: Box::new(Value::SimpleString(message)),
        attributes: attribute
            .into_iter()
            .map(|(key, value)| (Value::SimpleString(key), Value::SimpleString(value)))
            .collect(),
    })) as *mut Value;
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_bigint(big_int: BigInt) -> [u32; 2] {
    let pointer = Box::leak(Box::new(Value::BigNumber(num_bigint::BigInt::new(
        if big_int.sign_bit {
            num_bigint::Sign::Minus
        } else {
            num_bigint::Sign::Plus
        },
        big_int
            .words
            .into_iter()
            .flat_map(|word| {
                let bytes = u64::to_le_bytes(word);
                unsafe { std::mem::transmute::<[u8; 8], [u32; 2]>(bytes) }
            })
            .collect(),
    )))) as *mut Value;
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_double(float: f64) -> [u32; 2] {
    let pointer = Box::leak(Box::new(Value::Double(float))) as *mut Value;
    split_pointer(pointer)
}

#[napi]
/// A wrapper for a script object. As long as this object is alive, the script's code is saved in memory, and can be resent to the server.
struct Script {
    hash: String,
}

#[napi]
impl Script {
    /// Construct with the script's code.
    #[napi(constructor)]
    #[allow(dead_code)]
    pub fn new(code: String) -> Self {
        let hash = glide_core::scripts_container::add_script(&code);
        Self { hash }
    }

    /// Returns the hash of the script.
    #[napi]
    #[allow(dead_code)]
    pub fn get_hash(&self) -> String {
        self.hash.clone()
    }
}

impl Drop for Script {
    fn drop(&mut self) {
        glide_core::scripts_container::remove_script(&self.hash);
    }
}
