// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::client::FINISHED_SCAN_CURSOR;
use glide_core::errors::error_message;
// Protocol constants for Java (defined directly since we don't use socket layer)
const TYPE_HASH: &str = "hash";
const TYPE_LIST: &str = "list";
const TYPE_SET: &str = "set";
const TYPE_STREAM: &str = "stream";
const TYPE_STRING: &str = "string";
const TYPE_ZSET: &str = "zset";
const MAX_REQUEST_ARGS_LENGTH_IN_BYTES: usize = 2_i32.pow(12) as usize; // 4096 bytes

// Telemetry required for getStatistics
use glide_core::Telemetry;
use protobuf::Message;

use jni::JNIEnv;
use jni::errors::Error as JniError;
use jni::objects::{
    GlobalRef, JByteArray, JClass, JMethodID, JObject, JObjectArray, JStaticMethodID, JString,
};
use jni::sys::{jint, jlong};
use parking_lot::Mutex;
use redis::Value;
use std::str::FromStr;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, OnceLock};

mod address_resolver;
mod errors;
mod iam_token_callback;
mod jni_client;
mod jni_pool;
mod jni_scope;
mod linked_hashmap;
mod routing;

use errors::{FFIError, handle_errors, run_ffi};
use jni_client::*;

use crate::address_resolver::JavaAddressResolver;
use crate::iam_token_callback::{JavaIamTokenCallback, make_iam_provider_callback};
/// Process command arguments for compression, matching the socket_listener pattern.
/// Extracts args from the command, applies compression if applicable, and rebuilds the command.
fn process_command_for_compression(
    cmd: &mut redis::Cmd,
    client: &glide_core::client::Client,
) -> Result<(), glide_core::compression::CompressionError> {
    let compression_manager = client.compression_manager();
    let compression_manager_ref = compression_manager.as_deref();

    // If compression is not enabled, skip all processing
    if compression_manager_ref
        .map(|m| !m.is_enabled())
        .unwrap_or(true)
    {
        return Ok(());
    }

    let all_args: Vec<Vec<u8>> = cmd
        .args_iter()
        .filter_map(|arg| match arg {
            redis::Arg::Simple(bytes) => Some(bytes.to_vec()),
            redis::Arg::Cursor => None,
        })
        .collect();

    if all_args.is_empty() {
        return Ok(());
    }

    let command_name = &all_args[0];
    let command_str = String::from_utf8_lossy(command_name);

    let request_type = match glide_core::request_type::RequestType::from_command_name(&command_str)
    {
        Some(rt) => rt,
        None => return Ok(()), // Unknown command - no compression processing needed
    };

    // Check if the command is incompatible with compression - this should error out
    glide_core::compression::validate_command_compression_compatibility(
        request_type,
        compression_manager_ref,
    )?;

    let mut args: Vec<Vec<u8>> = all_args[1..].to_vec();
    glide_core::compression::process_command_args_for_compression(
        &mut args,
        request_type,
        compression_manager_ref,
    )?;

    *cmd = redis::Cmd::new();
    cmd.arg(command_name);
    for arg in args {
        cmd.arg(arg);
    }
    Ok(())
}

#[derive(Clone)]
pub struct RegistryMethodCache {
    class: GlobalRef,
    retrieve_method: JStaticMethodID,
}

static REGISTRY_METHOD_CACHE: OnceLock<Mutex<Option<RegistryMethodCache>>> = OnceLock::new();

fn get_registry_method_cache(env: &mut JNIEnv) -> Result<&'static RegistryMethodCache, FFIError> {
    let cache_mutex = REGISTRY_METHOD_CACHE.get_or_init(|| Mutex::new(None));
    {
        let guard = cache_mutex.lock();
        if let Some(ref cache) = *guard {
            return Ok(unsafe {
                std::mem::transmute::<&RegistryMethodCache, &RegistryMethodCache>(cache)
            });
        }
    }

    let class = env.find_class("glide/managers/JniResponseRegistry")?;
    let global_class = env.new_global_ref(&class)?;
    let method = env.get_static_method_id(&class, "retrieveAndRemove", "(J)Ljava/lang/Object;")?;

    let cache = RegistryMethodCache {
        class: global_class,
        retrieve_method: method,
    };

    // If another thread initialized concurrently, prefer the existing value.
    {
        let mut guard = cache_mutex.lock();
        if guard.is_none() {
            *guard = Some(cache);
        }
    }

    let guard = cache_mutex.lock();
    let cache_ref = guard
        .as_ref()
        .expect("RegistryMethodCache should be initialized");
    Ok(unsafe { std::mem::transmute::<&RegistryMethodCache, &RegistryMethodCache>(cache_ref) })
}

/// Get registry method cache using correct classloader context
fn get_registry_method_cache_safe(
    fallback_env: &mut JNIEnv,
) -> Result<&'static RegistryMethodCache, FFIError> {
    // Try cached JVM env first
    if let Some(cached_jvm) = jni_client::JVM.get()
        && let Ok(mut cached_env) = cached_jvm.get_env()
    {
        return get_registry_method_cache(&mut cached_env);
    }
    // Otherwise fallback to provided env
    get_registry_method_cache(fallback_env)
}

/// Complete a callback with an error directly on the calling (JNI) thread.
/// Logs the error and propagates it to Java. Used in pre-spawn error paths.
fn complete_callback_with_error_on_caller(env: &mut JNIEnv, callback_id: jlong, error_msg: &str) {
    log::error!("{error_msg}");
    let error_code = 0; // RequestException (Unspecified)
    if let Err(e) = complete_java_callback_with_error_code(env, callback_id, error_code, error_msg)
    {
        log::error!("Failed to complete callback {callback_id} with error: {e}");
    }
}

/// Completes the callback with an error if both fail.
fn get_jvm_or_complete_error(
    env: &mut JNIEnv,
    callback_id: jlong,
    fn_name: &str,
) -> Option<Arc<jni::JavaVM>> {
    match env.get_java_vm() {
        Ok(jvm) => Some(Arc::new(jvm)),
        Err(e) => match JVM.get().cloned() {
            Some(jvm) => Some(jvm),
            None => {
                let msg = format!("JVM unavailable in {fn_name}: {e}");
                complete_callback_with_error_on_caller(env, callback_id, &msg);
                None
            }
        },
    }
}

/// Configuration for OpenTelemetry integration in the Java client.
///
/// This struct allows you to configure how telemetry data (traces and metrics) is exported to an OpenTelemetry collector.
/// - `traces`: Optional configuration for exporting trace data. If `None`, trace data will not be exported.
/// - `metrics`: Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
/// - `flush_interval_ms`: Optional interval in milliseconds between consecutive exports of telemetry data. If `None`, a default value will be used.
///
/// At least one of traces or metrics must be provided.
#[derive(Clone)]
pub struct OpenTelemetryConfig {
    /// Optional configuration for exporting trace data. If `None`, trace data will not be exported.
    pub traces: Option<OpenTelemetryTracesConfig>,
    /// Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
    pub metrics: Option<OpenTelemetryMetricsConfig>,
    /// Optional interval in milliseconds between consecutive exports of telemetry data. If `None`, the default `DEFAULT_FLUSH_SIGNAL_INTERVAL_MS` will be used.
    pub flush_interval_ms: Option<i64>,
}

/// Configuration for exporting OpenTelemetry traces.
///
/// - `endpoint`: The endpoint to which trace data will be exported. Expected format:
///   - For gRPC: `grpc://host:port`
///   - For HTTP: `http://host:port` or `https://host:port`
///   - For file exporter: `file:///absolute/path/to/folder/file.json`
/// - `sample_percentage`: The percentage of requests to sample and create a span for, used to measure command duration. If `None`, a default value DEFAULT_TRACE_SAMPLE_PERCENTAGE will be used.
///   Note: There is a tradeoff between sampling percentage and performance. Higher sampling percentages will provide more detailed telemetry data but will impact performance.
///   It is recommended to keep this number low (1-5%) in production environments unless you have specific needs for higher sampling rates.
#[derive(Clone)]
pub struct OpenTelemetryTracesConfig {
    /// The endpoint to which trace data will be exported.
    pub endpoint: String,
    /// The percentage of requests to sample and create a span for, used to measure command duration. If `None`, a default value DEFAULT_TRACE_SAMPLE_PERCENTAGE will be used.
    pub sample_percentage: Option<u32>,
}

/// Configuration for exporting OpenTelemetry metrics.
///
/// - `endpoint`: The endpoint to which metrics data will be exported. Expected format:
///   - For gRPC: `grpc://host:port`
///   - For HTTP: `http://host:port` or `https://host:port`
///   - For file exporter: `file:///absolute/path/to/folder/file.json`
#[derive(Clone)]
pub struct OpenTelemetryMetricsConfig {
    /// The endpoint to which metrics data will be exported.
    pub endpoint: String,
}
struct Level(i32);

fn resp_value_to_java<'local>(
    env: &mut JNIEnv<'local>,
    val: Value,
    encoding_utf8: bool,
) -> Result<JObject<'local>, FFIError> {
    match val {
        Value::Nil => Ok(JObject::null()),
        Value::SimpleString(data) => {
            if encoding_utf8 {
                if data.eq_ignore_ascii_case("ok") {
                    let ok = get_ok_jstring(env)?;
                    Ok(JObject::from(ok))
                } else {
                    Ok(JObject::from(env.new_string(data)?))
                }
            } else {
                // Return raw byte array - Java will convert to GlideString
                Ok(JObject::from(env.byte_array_from_slice(data.as_bytes())?))
            }
        }
        Value::Okay => {
            let ok = get_ok_jstring(env)?;
            Ok(JObject::from(ok))
        }
        Value::Int(num) => {
            let cache = get_java_value_conversion_cache_safe(env)?;
            let cls = to_local_jclass(env, &cache.long_class)?;
            let arg = jni::sys::jvalue {
                j: num as jni::sys::jlong,
            };
            let obj = unsafe { env.new_object_unchecked(cls, cache.long_ctor, &[arg])? };
            Ok(obj)
        }
        Value::BulkString(data) => {
            if encoding_utf8 {
                match String::from_utf8(data.to_vec()) {
                    Ok(utf8_str) => Ok(JObject::from(env.new_string(utf8_str)?)),
                    Err(err) => {
                        let bytes = err.into_bytes();
                        Ok(JObject::from(env.byte_array_from_slice(&bytes)?))
                    }
                }
            } else {
                Ok(JObject::from(env.byte_array_from_slice(&data)?))
            }
        }
        Value::Array(array) => array_to_java_array(env, array, encoding_utf8),
        Value::Map(map) => {
            let cache = get_java_value_conversion_cache_safe(env)?;
            let cls = to_local_jclass(env, &cache.linked_hash_map_class)?;
            let linked_hash_map =
                unsafe { env.new_object_unchecked(cls, cache.linked_hash_map_ctor, &[])? };

            for (key, value) in map {
                let java_key = resp_value_to_java(env, key, encoding_utf8)?;
                let java_value = resp_value_to_java(env, value, encoding_utf8)?;
                let key_raw = java_key.into_raw();
                let val_raw = java_value.into_raw();
                unsafe {
                    env.call_method_unchecked(
                        &linked_hash_map,
                        cache.linked_hash_map_put,
                        jni::signature::ReturnType::Object,
                        &[
                            jni::sys::jvalue { l: key_raw },
                            jni::sys::jvalue { l: val_raw },
                        ],
                    )?;
                }
                let _ = unsafe { JObject::from_raw(key_raw) };
                let _ = unsafe { JObject::from_raw(val_raw) };
            }

            Ok(linked_hash_map)
        }
        Value::Double(float) => {
            let cache = get_java_value_conversion_cache_safe(env)?;
            // Use cached Double.valueOf for minimal overhead
            let jclass = to_local_jclass(env, &cache.double_class)?;
            let obj = unsafe {
                env.call_static_method_unchecked(
                    &jclass,
                    cache.double_value_of,
                    jni::signature::ReturnType::Object,
                    &[jni::sys::jvalue { d: float }],
                )?
                .l()?
            };
            Ok(obj)
        }
        Value::Boolean(bool) => {
            let cache = get_java_value_conversion_cache_safe(env)?;
            let jclass = to_local_jclass(env, &cache.boolean_class)?;
            let z = if bool { 1 } else { 0 };
            let obj = unsafe {
                env.call_static_method_unchecked(
                    &jclass,
                    cache.boolean_value_of,
                    jni::signature::ReturnType::Object,
                    &[jni::sys::jvalue { z }],
                )?
                .l()?
            };
            Ok(obj)
        }
        Value::VerbatimString { format: _, text } => {
            if encoding_utf8 {
                Ok(JObject::from(env.new_string(text)?))
            } else {
                Ok(JObject::from(env.byte_array_from_slice(text.as_bytes())?))
            }
        }
        Value::BigNumber(num) => {
            // Convert Valkey BigNumber to Java BigInteger
            // BigNumbers in Valkey are represented as strings
            let big_int_str = num.to_string();
            let java_string = env.new_string(big_int_str)?;
            let cache = get_java_value_conversion_cache_safe(env)?;
            let cls = to_local_jclass(env, &cache.big_integer_class)?;
            let raw = java_string.into_raw();
            let obj = unsafe {
                env.new_object_unchecked(
                    cls,
                    cache.big_integer_ctor,
                    &[jni::sys::jvalue { l: raw }],
                )?
            };
            // Recreate to allow drop and avoid leaking the local ref
            let _ = unsafe { JString::from_raw(raw) };
            Ok(obj)
        }
        Value::Set(array) => {
            let cache = get_java_value_conversion_cache_safe(env)?;
            let cls = to_local_jclass(env, &cache.hash_set_class)?;
            let set = unsafe { env.new_object_unchecked(cls, cache.hash_set_ctor, &[])? };

            for elem in array {
                let java_value = resp_value_to_java(env, elem, encoding_utf8)?;
                let val_raw = java_value.into_raw();
                unsafe {
                    env.call_method_unchecked(
                        &set,
                        cache.hash_set_add,
                        jni::signature::ReturnType::Primitive(jni::signature::Primitive::Boolean),
                        &[jni::sys::jvalue { l: val_raw }],
                    )?;
                }
                let _ = unsafe { JObject::from_raw(val_raw) };
            }

            Ok(set)
        }
        Value::Attribute { data, attributes } => {
            // Convert Valkey Attribute to Java Map<String, Object>
            // Create a HashMap with both data and attributes
            let cache = get_java_value_conversion_cache_safe(env)?;
            let cls = to_local_jclass(env, &cache.hash_map_class)?;
            let hash_map = unsafe { env.new_object_unchecked(cls, cache.hash_map_ctor, &[])? };

            // Add the main data under "data" key
            let data_key = env.new_string("data")?;
            let java_data = resp_value_to_java(env, *data, encoding_utf8)?;
            let k_raw = data_key.into_raw();
            let v_raw = java_data.into_raw();
            unsafe {
                env.call_method_unchecked(
                    &hash_map,
                    cache.hash_map_put,
                    jni::signature::ReturnType::Object,
                    &[jni::sys::jvalue { l: k_raw }, jni::sys::jvalue { l: v_raw }],
                )?;
            }
            let _ = unsafe { JObject::from_raw(k_raw) };
            let _ = unsafe { JObject::from_raw(v_raw) };

            // Add the attributes under "attributes" key
            let attributes_key = env.new_string("attributes")?;
            let java_attributes = resp_value_to_java(env, Value::Map(attributes), encoding_utf8)?;
            let k_raw = attributes_key.into_raw();
            let v_raw = java_attributes.into_raw();
            unsafe {
                env.call_method_unchecked(
                    &hash_map,
                    cache.hash_map_put,
                    jni::signature::ReturnType::Object,
                    &[jni::sys::jvalue { l: k_raw }, jni::sys::jvalue { l: v_raw }],
                )?;
            }
            let _ = unsafe { JObject::from_raw(k_raw) };
            let _ = unsafe { JObject::from_raw(v_raw) };

            Ok(hash_map)
        }
        // Create a java `Map<String, Object>` with two keys:
        //   - "kind" which corresponds to the push type, stored as a `String`
        //   - "values" which corresponds to the array of values received, stored as `Object[]`
        // Only string messages are supported now by Valkey and `redis-rs`.
        Value::Push { kind, data } => {
            let cache = get_java_value_conversion_cache_safe(env)?;
            let cls = to_local_jclass(env, &cache.hash_map_class)?;
            let hash_map = unsafe { env.new_object_unchecked(cls, cache.hash_map_ctor, &[])? };

            let kind_str = env.new_string("kind")?;
            let kind_value_str = env.new_string(format!("{kind:?}"))?;

            let k_raw = kind_str.into_raw();
            let v_raw = kind_value_str.into_raw();
            unsafe {
                env.call_method_unchecked(
                    &hash_map,
                    cache.hash_map_put,
                    jni::signature::ReturnType::Object,
                    &[jni::sys::jvalue { l: k_raw }, jni::sys::jvalue { l: v_raw }],
                )?;
            }
            let _ = unsafe { JObject::from_raw(k_raw) };
            let _ = unsafe { JObject::from_raw(v_raw) };
            let _ = 0;

            let values_str = env.new_string("values")?;
            let values = array_to_java_array(env, data, encoding_utf8)?;

            let k_raw = values_str.into_raw();
            let v_raw = values.into_raw();
            unsafe {
                env.call_method_unchecked(
                    &hash_map,
                    cache.hash_map_put,
                    jni::signature::ReturnType::Object,
                    &[jni::sys::jvalue { l: k_raw }, jni::sys::jvalue { l: v_raw }],
                )?;
            }
            let _ = unsafe { JObject::from_raw(k_raw) };
            let _ = unsafe { JObject::from_raw(v_raw) };
            let _ = 0;

            Ok(hash_map)
        }
        Value::ServerError(server_error) => {
            let err_msg = error_message(&server_error.into());
            let jmsg = env.new_string(err_msg)?;
            let cache = get_java_value_conversion_cache_safe(env)?;
            let cls = to_local_jclass(env, &cache.request_exception_class)?;
            let raw = jmsg.into_raw();
            let obj = unsafe {
                env.new_object_unchecked(
                    cls,
                    cache.request_exception_ctor,
                    &[jni::sys::jvalue { l: raw }],
                )?
            };
            // Recreate to allow drop and avoid leaking the local ref
            let _ = unsafe { JString::from_raw(raw) };
            Ok(obj)
        }
    }
}

/// Convert an array of values into java array of corresponding values.
///
/// Recursively calls to [`resp_value_to_java`] for every element.
///
/// Returns an arbitrary java `Object[]`.
fn array_to_java_array<'local>(
    env: &mut JNIEnv<'local>,
    values: Vec<Value>,
    encoding_utf8: bool,
) -> Result<JObject<'local>, FFIError> {
    let items: JObjectArray =
        env.new_object_array(values.len() as i32, "java/lang/Object", JObject::null())?;

    for (i, item) in values.into_iter().enumerate() {
        let java_value = resp_value_to_java(env, item, encoding_utf8)?;
        env.set_object_array_element(&items, i as i32, java_value)?;
    }

    Ok(items.into())
}

/// RAII guard that clears the `is_blocking` flag when dropped.
/// Ensures the flag is always unset on every exit path from a blocking
/// command dispatch (normal completion, early return, cancellation).
struct UnmarkOnDrop(Option<Arc<AtomicBool>>);
impl Drop for UnmarkOnDrop {
    fn drop(&mut self) {
        if let Some(arc) = self.0.take() {
            arc.store(false, std::sync::atomic::Ordering::Release);
        }
    }
}

/// Returns the maximum total length in bytes of request arguments.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `_env`    - The JNI environment. Not used.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideValueResolver_getMaxRequestArgsLengthInBytes<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    MAX_REQUEST_ARGS_LENGTH_IN_BYTES as jlong
}

/// Convert a Valkey Value pointer to a Java object with UTF-8 string encoding.
///
/// This function is meant to be invoked by Java using JNI.
///
/// * `env`     - The JNI environment.
/// * `_class`  - The class object. Not used.
/// * `pointer` - A pointer to a Valkey Value object.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideValueResolver_valueFromPointer<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    run_ffi(|| {
        fn value_from_pointer<'a>(
            env: &mut JNIEnv<'a>,
            pointer: jlong,
        ) -> Result<JObject<'a>, FFIError> {
            if pointer == 0 {
                return Ok(JObject::null());
            }

            let cache = get_registry_method_cache_safe(env)?;
            let class_j = to_local_jclass(env, &cache.class)?;
            let result = unsafe {
                env.call_static_method_unchecked(
                    class_j,
                    cache.retrieve_method,
                    jni::signature::ReturnType::Object,
                    &[jni::sys::jvalue { j: pointer }],
                )
            }?;

            match result {
                jni::objects::JValueGen::Object(obj) => Ok(obj),
                _ => Ok(JObject::null()),
            }
        }
        let result = value_from_pointer(&mut env, pointer);
        handle_errors(&mut env, result)
    })
    .unwrap_or(JObject::null())
}

/// Convert a Redis Value pointer to a Java object with binary (byte[]) encoding.
///
/// This function is meant to be invoked by Java using JNI.
///
/// * `env`     - The JNI environment.
/// * `_class`  - The class object. Not used.
/// * `pointer` - A pointer to a Valkey Value object.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideValueResolver_valueFromPointerBinary<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    run_ffi(|| {
        fn value_from_pointer_binary<'a>(
            env: &mut JNIEnv<'a>,
            pointer: jlong,
        ) -> Result<JObject<'a>, FFIError> {
            if pointer == 0 {
                return Ok(JObject::null());
            }

            let cache = get_registry_method_cache_safe(env)?;
            let class_j = to_local_jclass(env, &cache.class)?;
            let result = unsafe {
                env.call_static_method_unchecked(
                    class_j,
                    cache.retrieve_method,
                    jni::signature::ReturnType::Object,
                    &[jni::sys::jvalue { j: pointer }],
                )
            }?;

            match result {
                jni::objects::JValueGen::Object(obj) => Ok(obj),
                _ => Ok(JObject::null()),
            }
        }
        let result = value_from_pointer_binary(&mut env, pointer);
        handle_errors(&mut env, result)
    })
    .unwrap_or(JObject::null())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_storeScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    code: JByteArray<'local>,
) -> JString<'local> {
    run_ffi(|| {
        fn store_script<'a>(
            env: &mut JNIEnv<'a>,
            code: JByteArray<'a>,
        ) -> Result<JString<'a>, FFIError> {
            let bytes = env.convert_byte_array(&code)?;
            let hash = glide_core::scripts_container::add_script(&bytes);
            Ok(env.new_string(hash)?)
        }
        let result = store_script(&mut env, code);
        handle_errors(&mut env, result)
    })
    .unwrap_or(JString::<'_>::default())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_dropScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    sha1: JString<'local>,
) {
    run_ffi(|| {
        fn drop_script(env: &mut JNIEnv<'_>, sha1: JString<'_>) -> Result<(), FFIError> {
            let sha: String = env.get_string(&sha1)?.into();
            glide_core::scripts_container::remove_script(&sha);
            Ok(())
        }
        let result = drop_script(&mut env, sha1);
        handle_errors(&mut env, result)
    })
    .unwrap_or(())
}

impl From<logger_core::Level> for Level {
    fn from(level: logger_core::Level) -> Self {
        match level {
            logger_core::Level::Error => Level(0),
            logger_core::Level::Warn => Level(1),
            logger_core::Level::Info => Level(2),
            logger_core::Level::Debug => Level(3),
            logger_core::Level::Trace => Level(4),
            logger_core::Level::Off => Level(5),
        }
    }
}

impl TryFrom<Level> for logger_core::Level {
    type Error = FFIError;
    fn try_from(level: Level) -> Result<Self, <logger_core::Level as TryFrom<Level>>::Error> {
        match level.0 {
            0 => Ok(logger_core::Level::Error),
            1 => Ok(logger_core::Level::Warn),
            2 => Ok(logger_core::Level::Info),
            3 => Ok(logger_core::Level::Debug),
            4 => Ok(logger_core::Level::Trace),
            5 => Ok(logger_core::Level::Off),
            _ => Err(FFIError::Logger(format!(
                "Invalid log level: {:?}",
                level.0
            ))),
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_LoggerResolver_logInternal<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    level: jint,
    log_identifier: JString<'local>,
    message: JString<'local>,
) {
    run_ffi(|| {
        fn log_internal(
            env: &mut JNIEnv<'_>,
            level: jint,
            log_identifier: JString<'_>,
            message: JString<'_>,
        ) -> Result<(), FFIError> {
            let level = Level(level);

            let log_identifier: String = env.get_string(&log_identifier)?.into();

            let message: String = env.get_string(&message)?.into();

            logger_core::log(level.try_into()?, log_identifier, message);
            Ok(())
        }
        let result = log_internal(&mut env, level, log_identifier, message);
        handle_errors(&mut env, result)
    })
    .unwrap_or(())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_LoggerResolver_initInternal<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    level: jint,
    file_name: JString<'local>,
) -> jint {
    run_ffi(|| {
        fn init_internal(
            env: &mut JNIEnv<'_>,
            level: jint,
            file_name: JString<'_>,
        ) -> Result<jint, FFIError> {
            let level = if level >= 0 { Some(level) } else { None };
            let file_name: Option<String> = match env.get_string(&file_name) {
                Ok(file_name) => Some(file_name.into()),
                Err(JniError::NullPtr(_)) => None,
                Err(err) => return Err(err.into()),
            };
            let level = match level {
                Some(lvl) => Some(Level(lvl).try_into()?),
                None => None,
            };
            let logger_level = logger_core::init(level, file_name.as_deref());
            Ok(Level::from(logger_level).0)
        }
        let result = init_internal(&mut env, level, file_name);
        handle_errors(&mut env, result)
    })
    .unwrap_or(0)
}

/// Releases a ClusterScanCursor handle allocated in Rust.
///
/// This function is meant to be invoked by Java using JNI.
///
/// * `_env`    - The JNI environment. Not used.
/// * `_class`  - The class object. Not used.
/// * cursor      - The cursor handle to release.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ClusterScanCursorResolver_releaseNativeCursor<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    cursor: JString<'local>,
) {
    run_ffi(|| {
        fn release_native_cursor(
            env: &mut JNIEnv<'_>,
            cursor: JString<'_>,
        ) -> Result<(), FFIError> {
            let cursor_str: String = env.get_string(&cursor)?.into();
            glide_core::cluster_scan_container::remove_scan_state_cursor(cursor_str);
            Ok(())
        }
        let result = release_native_cursor(&mut env, cursor);
        handle_errors(&mut env, result)
    })
    .unwrap_or(())
}

/// Returns the String representing a finished cursor handle.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ClusterScanCursorResolver_getFinishedCursorHandleConstant<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, FINISHED_SCAN_CURSOR)
}

/// Returns the String representing the name of the ObjectType String.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeStringConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_STRING)
}

/// Returns the String representing the name of the ObjectType List.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeListConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_LIST)
}

/// Returns the String representing the name of the ObjectType Set.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeSetConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_SET)
}

/// Returns the String representing the name of the ObjectType ZSet.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeZSetConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_ZSET)
}

/// Returns the String representing the name of the ObjectType Hash.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeHashConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_HASH)
}

/// Returns the String representing the name of the ObjectType Set.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeStreamConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_STREAM)
}

/// Returns a Java's `HashMap` representing the statistics collected for this process.
///
/// This function is meant to be invoked by Java using JNI.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_StatisticsResolver_getStatistics<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JObject<'local> {
    let Some(mut map) = linked_hashmap::new_linked_hashmap(&mut env) else {
        return JObject::null();
    };

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "total_connections",
        &format!("{}", Telemetry::total_connections()),
    );

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "total_clients",
        &format!("{}", Telemetry::total_clients()),
    );

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "total_values_compressed",
        &format!("{}", Telemetry::total_values_compressed()),
    );

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "total_values_decompressed",
        &format!("{}", Telemetry::total_values_decompressed()),
    );

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "total_original_bytes",
        &format!("{}", Telemetry::total_original_bytes()),
    );

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "total_bytes_compressed",
        &format!("{}", Telemetry::total_bytes_compressed()),
    );

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "total_bytes_decompressed",
        &format!("{}", Telemetry::total_bytes_decompressed()),
    );

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "compression_skipped_count",
        &format!("{}", Telemetry::compression_skipped_count()),
    );

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "subscription_out_of_sync_count",
        &format!("{}", Telemetry::subscription_out_of_sync_count()),
    );

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "subscription_last_sync_timestamp",
        &format!("{}", Telemetry::subscription_last_sync_timestamp()),
    );

    map
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_OpenTelemetryResolver_initOpenTelemetry<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    traces_endpoint: JString<'local>,
    traces_sample_percentage: jint,
    metrics_endpoint: JString<'local>,
    flush_interval_ms: jlong,
) -> jint {
    run_ffi(|| {
            fn init_open_telemetry<'a>(
                env: &mut JNIEnv<'a>,
                traces_endpoint: JString<'a>,
                traces_sample_percentage: jint,
                metrics_endpoint: JString<'a>,
                flush_interval_ms: jlong,
            ) -> Result<jint, FFIError> {
                // Convert JString to Rust String or None if null
                let traces_endpoint: Option<String> = match env.get_string(&traces_endpoint) {
                    Ok(endpoint) => Some(endpoint.into()),
                    Err(JniError::NullPtr(_)) => None,
                    Err(err) => return Err(err.into()),
                };

                let metrics_endpoint: Option<String> = match env.get_string(&metrics_endpoint) {
                    Ok(endpoint) => Some(endpoint.into()),
                    Err(JniError::NullPtr(_)) => None,
                    Err(err) => return Err(err.into()),
                };

                // Validate that at least one endpoint is provided
                if traces_endpoint.is_none() && metrics_endpoint.is_none() {
                    return Err(FFIError::OpenTelemetry(
                        "At least one of traces or metrics must be provided for OpenTelemetry configuration.".to_string(),
                    ));
                }
                // Validate flush interval
                if flush_interval_ms <= 0 {
                    return Err(FFIError::OpenTelemetry(format!(
                        "InvalidInput: flushIntervalMs must be a positive integer (got: {flush_interval_ms})"
                    )));
                }

                let mut config = glide_core::GlideOpenTelemetryConfigBuilder::default();

                // Initialize traces exporter if endpoint is provided
                if let Some(endpoint) = traces_endpoint {
                    config = config.with_trace_exporter(
                        glide_core::GlideOpenTelemetrySignalsExporter::from_str(&endpoint)
                            .map_err(|e| FFIError::OpenTelemetry(format!("{e}")))?,
                        if traces_sample_percentage >= 0 {
                            Some(traces_sample_percentage as u32)
                        } else {
                            return Err(FFIError::OpenTelemetry(format!(
                                "InvalidInput: traces_sample_percentage must be a positive integer (got: {traces_sample_percentage})"
                                ))
                            );
                        },
                    );
                }

                // Initialize metrics exporter if endpoint is provided
                if let Some(endpoint) = metrics_endpoint {
                    config = config.with_metrics_exporter(
                        glide_core::GlideOpenTelemetrySignalsExporter::from_str(&endpoint)
                            .map_err(|e| FFIError::OpenTelemetry(format!("{e}")))?,
                    );
                }

                // Set flush interval
                config = config.with_flush_interval(std::time::Duration::from_millis(flush_interval_ms as u64));

                // Initialize OpenTelemetry
                let glide_rt = match glide_core::client::get_or_init_runtime() {
                    Ok(handle) => handle,
                    Err(err) => {
                        return Err(FFIError::OpenTelemetry(format!(
                            "Failed to get or init runtime: {err}"
                        )))
                    }
                };

                glide_rt.runtime.block_on(async {
                    if let Err(e) = glide_core::GlideOpenTelemetry::initialise(config.build()) {
                        logger_core::log(
                            logger_core::Level::Error,
                            "OpenTelemetry",
                            format!("Failed to initialize OpenTelemetry: {e}"),
                        );
                        return Err(FFIError::OpenTelemetry(format!(
                            "Failed to initialize OpenTelemetry: {e}"
                        )));
                    }
                    Ok(())
                })?;

                Ok(0 as jint)
            }
            let result = init_open_telemetry(&mut env, traces_endpoint, traces_sample_percentage, metrics_endpoint, flush_interval_ms);
            handle_errors(&mut env, result)
        },
    )
    .unwrap_or(0 as jint)
}

/// Creates an open telemetry span with the given name and returns a pointer to the span
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_OpenTelemetryResolver_createLeakedOtelSpan<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    name: JString<'local>,
) -> jlong {
    run_ffi(|| {
        fn create_leaked_otel_span<'a>(
            env: &mut JNIEnv<'a>,
            name: JString<'a>,
        ) -> Result<jlong, FFIError> {
            let name_str: String = env.get_string(&name)?.into();
            let span = glide_core::GlideOpenTelemetry::new_span(&name_str);
            let s = Arc::into_raw(Arc::new(span)) as *mut glide_core::GlideSpan;
            Ok(s as jlong)
        }
        let result = create_leaked_otel_span(&mut env, name);
        handle_errors(&mut env, result)
    })
    .unwrap_or(0)
}

/// Drops an OpenTelemetry span given its pointer
/// # Safety
/// * `span_ptr` must not be `null`.
/// * `span_ptr` must be able to be safely casted to a valid [`Arc<glide_core::GlideSpan>`] via [`Arc::from_raw`]. See the safety documentation of [`Arc::from_raw`].
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_glide_ffi_resolvers_OpenTelemetryResolver_dropOtelSpan<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    span_ptr: jlong,
) {
    run_ffi(|| {
        fn drop_otel_span(span_ptr: jlong) -> Result<(), FFIError> {
            if span_ptr <= 0 {
                return Err(FFIError::OpenTelemetry(
                    "Received an invalid pointer value.".to_string(),
                ));
            }

            let span_ptr_u64 = span_ptr as u64;
            if unsafe { !glide_core::GlideOpenTelemetry::is_span_pointer_valid(span_ptr_u64) } {
                return Err(FFIError::OpenTelemetry(format!(
                    "Received an invalid pointer value: {span_ptr}"
                )));
            }

            unsafe {
                Arc::from_raw(span_ptr as *const glide_core::GlideSpan);
            }
            Ok(())
        }
        let result = drop_otel_span(span_ptr);
        handle_errors(&mut env, result)
    })
    .unwrap_or(())
}

/// Convert a Rust string to a Java String and handle errors.
///
/// * `env`             - The JNI environment.
/// * `_class`          - The class object. Not used.
/// * `input`           - The String to convert.
/// * `functionName`    - The name of the calling function.
fn safe_create_jstring<'local>(mut env: JNIEnv<'local>, input: &str) -> JString<'local> {
    run_ffi(|| {
        fn create_jstring<'a>(env: &mut JNIEnv<'a>, input: &str) -> Result<JString<'a>, FFIError> {
            Ok(env.new_string(input)?)
        }
        let result = create_jstring(&mut env, input);
        handle_errors(&mut env, result)
    })
    .unwrap_or(JString::<'_>::default())
}

// ==================== JNI CLIENT MANAGEMENT FUNCTIONS ====================

/// Create Valkey client and store handle.
/// If address_resolver is not null, it will be stored as a global reference and used
/// for address resolution callbacks. The global reference ensures the resolver is not
/// garbage collected while the client is alive.
/// If iam_credentials_provider is not null, it will be stored as a global reference
/// and invoked whenever Rust needs to sign a fresh IAM token with custom credentials.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_createClient(
    mut env: JNIEnv,
    _class: JClass,
    connection_request_bytes: JByteArray,
    address_resolver: JObject,
    iam_credentials_provider: JObject,
) -> jlong {
    run_ffi(|| {
        // Convert Java byte array to Rust bytes
        let request_bytes = match env.convert_byte_array(&connection_request_bytes) {
            Ok(bytes) => bytes,
            Err(e) => {
                log::error!("Failed to convert byte array: {e}");
                return Some(0);
            }
        };

        // Parse ConnectionRequest protobuf
        let request = match glide_core::connection_request::ConnectionRequest::parse_from_bytes(
            &request_bytes,
        ) {
            Ok(req) => req,
            Err(e) => {
                log::error!("Failed to parse ConnectionRequest protobuf: {e}");
                return Some(0);
            }
        };

        // Convert protobuf to glide_core ConnectionRequest
        let mut connection_request = glide_core::client::ConnectionRequest::from(request);

        // Cache JVM for push callbacks
        if let Ok(jvm) = env.get_java_vm() {
            let _ = jni_client::JVM.set(Arc::new(jvm));
        }

        // If an address resolver is provided, create a global reference and set up the callback
        // The global reference ensures the Java object is not garbage collected while in use
        if !address_resolver.is_null()
            && let Some(jvm) = jni_client::JVM.get().cloned()
        {
            match JavaAddressResolver::new(&mut env, jvm, &address_resolver) {
                Some(resolver) => {
                    connection_request.address_resolver = Some(Arc::new(resolver));
                }
                None => {
                    return Some(0);
                }
            }
        }

        // If an IAM credentials provider is provided, wrap it so that Rust calls back
        // into Java whenever it needs to sign a fresh token with custom credentials.
        if !iam_credentials_provider.is_null() {
            match jni_client::JVM.get().cloned() {
                Some(jvm) => {
                    match JavaIamTokenCallback::new(&mut env, jvm, &iam_credentials_provider) {
                        Some(cb) => {
                            if let Some(auth_info) = connection_request.authentication_info.as_mut()
                                && let Some(iam_config) = auth_info.iam_config.as_mut()
                            {
                                iam_config.credentials_provider =
                                    Some(make_iam_provider_callback(cb));
                            } else {
                                log::error!(
                                    "IAM credentials provider supplied but no IAM configuration \
                                     found in the connection request. Refusing to create client \
                                     to prevent a silent fallback to the default AWS credential \
                                     chain."
                                );
                                return Some(0);
                            }
                        }
                        None => {
                            return Some(0);
                        }
                    }
                }
                None => {
                    log::error!(
                        "IAM credentials provider supplied but JVM is not initialised. \
                         Cannot inject custom credentials — refusing to fall back to the \
                         default AWS credential chain."
                    );
                    return Some(0);
                }
            }
        }

        // Direct client creation (no lazy loading for simplified implementation)
        let runtime = get_runtime();

        // Always create push channel to support dynamic subscriptions via customCommand
        // This matches the behavior of socket_listener.rs which always creates push channels
        let (tx, rx) = tokio::sync::mpsc::unbounded_channel::<redis::PushInfo>();

        match runtime.block_on(async { create_glide_client(connection_request, Some(tx)).await }) {
            Ok(client) => {
                let safe_handle = jni_client::generate_safe_handle();
                let handle_table = get_handle_table();

                // Store in handle table
                handle_table.insert(safe_handle, client.clone());

                // Register in scope client registry for scope command execution
                glide_core::scope::register_client(safe_handle, client);

                // Always spawn push forwarder to deliver pushes to Java
                let jvm_arc = jni_client::JVM.get().cloned();
                let handle_for_java = safe_handle as jlong;
                get_runtime().spawn(async move {
                    let mut rx = rx;
                    while let Some(push) = rx.recv().await {
                        if let Some(jvm) = jvm_arc.as_ref()
                            && let Ok(mut env) = jvm.attach_current_thread_as_daemon()
                        {
                            handle_push_notification(&mut env, handle_for_java, push);
                        }
                    }
                });

                Some(safe_handle as jlong)
            }
            Err(e) => {
                // Do not include the full error in the log to prevent leaking credential
                // material. The error is thrown as a Java exception so the caller can
                // inspect it.
                log::error!("Failed to create client.");
                // Throw a ClosingException so the caller receives the actual error detail
                // rather than a generic ClosingException("Connection refused") from handle==0.
                let msg = e.to_string();
                match env.exception_check() {
                    Ok(false) => {
                        if env
                            .throw_new("glide/api/models/exceptions/ClosingException", &msg)
                            .is_err()
                        {
                            log::error!(
                                "Failed to throw ClosingException for client creation error."
                            );
                        }
                    }
                    Ok(true) => {
                        // An exception is already pending; don't clobber it.
                    }
                    Err(err) => {
                        log::error!("Failed to check pending exception: {err}");
                    }
                }
                Some(0)
            }
        }
    })
    .unwrap_or(0)
}

/// Close client and release resources.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_closeClient(
    _env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
) {
    run_ffi(|| {
        let handle_table = get_handle_table();
        let handle_id = client_ptr as u64;

        // DashMap operations are sync and lock-free
        if let Some((_, client)) = handle_table.remove(&handle_id) {
            // Unregister from the scope client registry
            glide_core::scope::unregister_client(handle_id);

            // Schedule async cleanup
            let runtime = get_runtime();
            runtime.spawn(async move {
                // Drop the client; core will close connections via Drop implementations
                drop(client);
            });
        }
        Some(())
    })
    .unwrap_or(())
}

/// Check if client handle exists.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_isConnected(
    _env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
) -> jni::sys::jboolean {
    run_ffi(|| {
        let handle_table = get_handle_table();
        let handle_id = client_ptr as u64;
        if handle_table.contains_key(&handle_id) {
            Some(1)
        } else {
            Some(0)
        }
    })
    .unwrap_or(0)
}

/// Get client information from native layer.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getClientInfo<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    client_ptr: jlong,
) -> JString<'local> {
    run_ffi(|| {
        fn get_client_info<'a>(
            env: &mut JNIEnv<'a>,
            client_ptr: jlong,
        ) -> Result<JString<'a>, FFIError> {
            let handle_id = client_ptr as u64;
            let handle_table = get_handle_table();

            if handle_table.contains_key(&handle_id) {
                // Return basic client information
                let info = format!("Client handle: {}, Status: Connected", handle_id);
                Ok(env.new_string(info)?)
            } else {
                let info = format!("Client handle: {}, Status: Not found", handle_id);
                Ok(env.new_string(info)?)
            }
        }
        let result = get_client_info(&mut env, client_ptr);
        handle_errors(&mut env, result)
    })
    .unwrap_or(JString::default())
}

/// Get glide-core default connection timeout in milliseconds
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getGlideCoreDefaultConnectionTimeoutMs(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    // Return glide-core's default connection timeout in milliseconds
    glide_core::client::DEFAULT_CONNECTION_TIMEOUT.as_millis() as jlong
}

/// Get glide-core default request timeout in milliseconds
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getGlideCoreDefaultRequestTimeoutMs(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    // Return glide-core's default request timeout in milliseconds
    glide_core::client::DEFAULT_RESPONSE_TIMEOUT.as_millis() as jlong
}

/// Get glide-core default maximum inflight requests limit
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getGlideCoreDefaultMaxInflightRequests(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    // Return glide-core's default max inflight requests
    glide_core::client::DEFAULT_MAX_INFLIGHT_REQUESTS as jint
}

/// Mark a callback as timed out on the native side.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_markTimedOut(
    _env: JNIEnv,
    _class: JClass,
    callback_id: jlong,
) {
    jni_client::mark_callback_timed_out(callback_id);
}

/// Execute a batch (pipeline/transaction) asynchronously.
/// Takes command data directly via JNI arrays.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeBatchAsync(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    callback_id: jlong,
    request_types: jni::objects::JIntArray,
    args: JObjectArray, // byte[][][]
    is_atomic: jni::sys::jboolean,
    raise_on_error: jni::sys::jboolean,
    timeout: jint,
    retry_server_error: jni::sys::jboolean,
    retry_connection_error: jni::sys::jboolean,
    has_route: jni::sys::jboolean,
    route_type: jint,
    route_param: JString,
    expect_utf8: jni::sys::jboolean,
    span_ptr: jlong,
) {
    run_ffi(|| {
        let Some(jvm) = get_jvm_or_complete_error(&mut env, callback_id, "executeBatchAsync")
        else {
            return Some(());
        };

        let handle_id = client_ptr as u64;

        if let Some(entry) = crate::jni_pool::get_pool_client_map().get(&handle_id) {
            glide_core::pool::refresh_client_activity(*entry.value(), handle_id);
        }

        // Extract request types
        let cmd_count = match env.get_array_length(&request_types) {
            Ok(len) => len as usize,
            Err(e) => {
                jni_client::complete_error_sync(
                    &mut env,
                    callback_id,
                    &format!("Failed to get request types length: {e}"),
                    0,
                );
                return Some(());
            }
        };
        let mut req_types = vec![0i32; cmd_count];
        if let Err(e) = env.get_int_array_region(&request_types, 0, &mut req_types) {
            jni_client::complete_error_sync(
                &mut env,
                callback_id,
                &format!("Failed to read request types: {e}"),
                0,
            );
            return Some(());
        }

        // Extract args: byte[][][] -> Vec<Vec<Vec<u8>>>
        let all_args: Result<Vec<Vec<Vec<u8>>>, FFIError> = (|| {
            let mut result = Vec::with_capacity(cmd_count);
            for i in 0..cmd_count {
                let cmd_args_obj = env.get_object_array_element(&args, i as i32)?;
                let cmd_args_array = JObjectArray::from(cmd_args_obj);
                let arg_count = env.get_array_length(&cmd_args_array)? as usize;
                let mut cmd_args = Vec::with_capacity(arg_count);
                for j in 0..arg_count {
                    let arg_obj = env.get_object_array_element(&cmd_args_array, j as i32)?;
                    let arg_bytes = env.convert_byte_array(JByteArray::from(arg_obj))?;
                    cmd_args.push(arg_bytes);
                }
                result.push(cmd_args);
            }
            Ok(result)
        })();

        let all_args = match all_args {
            Ok(a) => a,
            Err(e) => {
                jni_client::complete_error_sync(
                    &mut env,
                    callback_id,
                    &format!("Failed to extract batch args: {e}"),
                    0,
                );
                return Some(());
            }
        };

        let is_atomic_bool = is_atomic != 0;
        let raise_on_error_bool = raise_on_error != 0;
        let timeout_val = if timeout > 0 {
            Some(timeout as u32)
        } else {
            None
        };
        let retry_server = retry_server_error != 0;
        let retry_connection = retry_connection_error != 0;
        let expect_utf8_bool = expect_utf8 != 0;

        // Extract route parameters
        let has_route_bool = has_route != 0;
        let route_type_val: i32 = route_type;
        let route_param_str: Option<String> = if !route_param.is_null() {
            match env.get_string(&route_param) {
                Ok(s) => Some(s.into()),
                Err(_) => None,
            }
        } else {
            None
        };

        // Synchronously detect if any command in the batch is a blocking command and
        // raise the flag BEFORE spawning, so the abandon monitor cannot observe
        // is_blocking=false in the window between spawn() and task execution.
        let batch_pre_blocking_arc: Option<Arc<AtomicBool>> = {
            let has_blocking = req_types.iter().enumerate().any(|(i, rt_int)| {
                let proto_rt = protobuf::EnumOrUnknown::<
                    glide_core::command_request::RequestType,
                >::from_i32(*rt_int);
                let rt: glide_core::request_type::RequestType = proto_rt.into();
                if matches!(rt, glide_core::request_type::RequestType::CustomCommand) {
                    // For CustomCommand the actual command name is all_args[i][0].
                    all_args
                        .get(i)
                        .and_then(|args| {
                            args.first().map(|name| {
                                let cmd_args = args.as_slice();
                                glide_core::client::is_blocking_command_name(
                                    name.as_slice(),
                                    if cmd_args.len() > 1 { &cmd_args[1..] } else { &[] },
                                )
                            })
                        })
                        .unwrap_or(false)
                } else {
                    // Build the full Cmd with arguments so that XREAD BLOCK and
                    // XREADGROUP BLOCK are detected correctly — they are only
                    // identifiable by the presence of the BLOCK keyword in the
                    // args, not by command name alone.
                    rt.get_command()
                        .map(|mut cmd| {
                            if let Some(args) = all_args.get(i) {
                                for arg in args {
                                    cmd.arg(arg.as_slice());
                                }
                            }
                            glide_core::client::is_blocking_command(&cmd)
                        })
                        .unwrap_or(false)
                }
            });
            if has_blocking {
                let arc = glide_core::pool::get_blocking_flag(handle_id);
                if let Some(ref a) = arc {
                    a.store(true, std::sync::atomic::Ordering::Release);
                }
                arc
            } else {
                None
            }
        };

        get_runtime().spawn(async move {
            // RAII guard: ensures is_blocking is cleared on every exit path —
            // client-not-found, routing/pipeline errors (via ?), normal completion,
            // or cancellation.
            // Guard arms immediately on task entry — flag was already set true before spawn.
            let _unmark_guard = UnmarkOnDrop(batch_pre_blocking_arc.clone());

            let client_result = jni_client::ensure_client_for_handle(handle_id).await;
            match client_result {
                Ok(mut client) => {
                    // Create "send_batch" child span for OTel tracing
                    let mut send_batch_span: Option<glide_core::GlideSpan> = None;
                    if span_ptr != 0
                        && let Ok(root_span) = unsafe {
                            glide_core::GlideOpenTelemetry::span_from_pointer(span_ptr as u64)
                        }
                        && let Ok(child) = root_span.add_span("send_batch")
                    {
                        send_batch_span = Some(child);
                    }

                    let result: Result<redis::Value, redis::RedisError> = async {
                        // Build pipeline directly from arrays
                        let mut pipeline = redis::Pipeline::with_capacity(cmd_count);
                        if is_atomic_bool {
                            pipeline.atomic();
                        }
                        if let Some(ref child) = send_batch_span {
                            pipeline.set_pipeline_span(Some(child.clone()));
                        }

                        for (i, rt) in req_types.iter().enumerate() {
                            let proto_rt = protobuf::EnumOrUnknown::<
                                glide_core::command_request::RequestType,
                            >::from_i32(*rt);
                            let request_type: glide_core::request_type::RequestType =
                                proto_rt.into();
                            let Some(mut cmd) = request_type.get_command() else {
                                return Err(redis::RedisError::from((
                                    redis::ErrorKind::ClientError,
                                    "Invalid request type in batch",
                                    format!("request_type={}", rt),
                                )));
                            };
                            for arg in &all_args[i] {
                                cmd.arg(arg.as_slice());
                            }
                            // Apply compression
                            #[allow(clippy::collapsible_if)]
                            if client.is_compression_enabled() {
                                if let Err(e) = process_command_for_compression(&mut cmd, &client) {
                                    if e.is_incompatible_command() {
                                        return Err(redis::RedisError::from((
                                            redis::ErrorKind::ClientError,
                                            "Incompatible command with compression",
                                            e.to_string(),
                                        )));
                                    }
                                }
                            }
                            pipeline.add_command(cmd);
                        }

                        // Compute routing
                        let routing = routing::resolve_routing_from_params(
                            has_route_bool,
                            route_type_val,
                            route_param_str.as_deref(),
                            None,
                        )
                        .map_err(|e| {
                            redis::RedisError::from((
                                redis::ErrorKind::ClientError,
                                "Routing error",
                                e.to_string(),
                            ))
                        })?;

                        // Use the pre-armed Arc (set synchronously before spawn). No need to
                        // re-fetch or re-set: the flag is already true if any command is
                        // blocking. The RAII guard at the top of the task handles clearing
                        // the flag on all exit paths.
                        let batch_blocking_arc = batch_pre_blocking_arc;

                        // Execute
                        let exec_res = if is_atomic_bool {
                            client
                                .send_transaction(
                                    &pipeline,
                                    routing,
                                    timeout_val,
                                    raise_on_error_bool,
                                )
                                .await
                        } else {
                            client
                                .send_pipeline(
                                    &pipeline,
                                    routing,
                                    raise_on_error_bool,
                                    timeout_val,
                                    redis::PipelineRetryStrategy {
                                        retry_server_error: retry_server,
                                        retry_connection_error: retry_connection,
                                    },
                                )
                                .await
                        };

                        // Refresh the activity timer after the batch completes so the
                        // abandon monitor doesn't reclaim the client immediately after a
                        // long-running blocking batch. Flag clearing is handled by the
                        // RAII guard on drop.
                        if batch_blocking_arc.is_some()
                            && let Some(entry) =
                                crate::jni_pool::get_pool_client_map().get(&handle_id)
                        {
                            glide_core::pool::refresh_client_activity(
                                *entry.value(),
                                handle_id,
                            );
                        }

                        // Decompress if needed
                        match exec_res {
                            Ok(value) => {
                                if client.is_compression_enabled() {
                                    if let Some(manager) = client.compression_manager() {
                                        match glide_core::compression::decompress_batch_response(
                                            value.clone(),
                                            manager.as_ref(),
                                        ) {
                                            Ok(decompressed) => Ok(decompressed),
                                            Err(e) => {
                                                logger_core::log_warn_rate_limited!(
                                                    "compression",
                                                    5,
                                                    format!("Failed to decompress batch response: {}, returning original", e)
                                                );
                                                Ok(value)
                                            }
                                        }
                                    } else {
                                        Ok(value)
                                    }
                                } else {
                                    Ok(value)
                                }
                            }
                            Err(e) => Err(e),
                        }
                    }
                    .await;

                    // End send_batch child span
                    if let Some(ref child) = send_batch_span {
                        child.end();
                    }

                    // End OpenTelemetry root span if one was created
                    if span_ptr != 0
                        && let Ok(span) = unsafe {
                            glide_core::GlideOpenTelemetry::span_from_pointer(span_ptr as u64)
                        }
                    {
                        span.end();
                        unsafe {
                            std::sync::Arc::from_raw(span_ptr as *const glide_core::GlideSpan);
                        }
                    }

                    complete_callback(jvm, callback_id, result, !expect_utf8_bool);
                }
                Err(err) => {
                    complete_callback(
                        jvm,
                        callback_id,
                        Err(redis::RedisError::from((
                            redis::ErrorKind::ClientError,
                            "Client not found",
                            err.to_string(),
                        ))),
                        !expect_utf8_bool,
                    );
                }
            }
        });

        Some(())
    })
    .unwrap_or(())
}

/// Execute a Valkey command asynchronously.
/// Takes command parameters directly via JNI: requestType as int, args as byte[][],
/// and routing as primitives.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeCommandAsync(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    callback_id: jlong,
    request_type: jint,
    args: JObjectArray,
    has_route: jni::sys::jboolean,
    route_type: jint,
    route_param: JString,
    expect_utf8: jni::sys::jboolean,
    span_ptr: jlong,
) {
    run_ffi(|| {
        let Some(jvm) = get_jvm_or_complete_error(&mut env, callback_id, "executeCommandAsync")
        else {
            return Some(());
        };

        let handle_id = client_ptr as u64;

        // Synchronous inflight check
        {
            let handle_table = jni_client::get_handle_table();
            if let Some(client_ref) = handle_table.get(&handle_id) {
                if client_ref.available_inflight_count() <= 0 {
                    drop(client_ref);
                    jni_client::complete_error_sync(
                        &mut env,
                        callback_id,
                        "Client reached maximum inflight requests",
                        0,
                    );
                    return Some(());
                }
                if !client_ref.is_circuit_breaker_healthy() {
                    drop(client_ref);
                    jni_client::complete_error_sync(
                        &mut env,
                        callback_id,
                        "Client circuit breaker is open - core unhealthy",
                        4,
                    );
                    return Some(());
                }
            }
        }

        // Extract args from byte[][]
        let args_vec: Result<Vec<Vec<u8>>, FFIError> = (|| {
            if args.is_null() {
                return Ok(Vec::new());
            }
            let length = env.get_array_length(&args)? as usize;
            let mut args_data = Vec::with_capacity(length);
            for i in 0..length {
                let arg_obj = env.get_object_array_element(&args, i as i32)?;
                let arg_bytes = env.convert_byte_array(JByteArray::from(arg_obj))?;
                args_data.push(arg_bytes);
            }
            Ok(args_data)
        })();

        let args_data = match args_vec {
            Ok(a) => a,
            Err(e) => {
                jni_client::complete_error_sync(
                    &mut env,
                    callback_id,
                    &format!("Failed to extract command args: {e}"),
                    0,
                );
                return Some(());
            }
        };

        // Extract route parameters
        let has_route_bool = has_route != 0;
        let route_type_val: i32 = route_type;
        let route_param_str: Option<String> = if !route_param.is_null() {
            match env.get_string(&route_param) {
                Ok(s) => Some(s.into()),
                Err(_) => None,
            }
        } else {
            None
        };

        let expect_utf8_bool = expect_utf8 != 0;

        // Synchronously detect if this is a blocking command and raise the flag
        // BEFORE spawning the async task, so the abandon monitor cannot observe
        // is_blocking=false in the window between spawn() and task execution on
        // a worker thread (Bug fix: pre-spawn flag race).
        let pre_blocking_arc: Option<Arc<AtomicBool>> = {
            let proto_rt =
                protobuf::EnumOrUnknown::<glide_core::command_request::RequestType>::from_i32(
                    request_type,
                );
            let rt: glide_core::request_type::RequestType = proto_rt.into();
            let is_blocking = if matches!(rt, glide_core::request_type::RequestType::CustomCommand)
            {
                // For CustomCommand the actual command name is args_data[0].
                // Use is_blocking_command_name with the remaining args so XREAD BLOCK is
                // handled correctly.
                args_data
                    .first()
                    .map(|name| {
                        glide_core::client::is_blocking_command_name(
                            name.as_slice(),
                            &args_data[1..],
                        )
                    })
                    .unwrap_or(false)
            } else if let Some(mut cmd) = rt.get_command() {
                // Append args so blocking-command detection works for arg-gated
                // commands like XREAD BLOCK and XREADGROUP BLOCK.
                for arg in &args_data {
                    cmd.arg(arg.as_slice());
                }
                glide_core::client::is_blocking_command(&cmd)
            } else {
                false
            };
            if is_blocking {
                let arc = glide_core::pool::get_blocking_flag(handle_id);
                if let Some(ref a) = arc {
                    a.store(true, std::sync::atomic::Ordering::Release);
                }
                arc
            } else {
                None
            }
        };

        get_runtime().spawn(async move {
            // Guard arms immediately on task entry — flag was already set true before spawn.
            // This ensures the flag is cleared on every exit path: normal completion,
            // early ?-return inside the inner async block, and task cancellation.
            // Safe: the pool enforces exclusive borrow — at most one blocking command
            // is in flight per pooled client at any time.
            let _unmark_guard = UnmarkOnDrop(pre_blocking_arc.clone());
            let result: Result<redis::Value, redis::RedisError> = async {
                let mut client = jni_client::ensure_client_for_handle(handle_id)
                    .await
                    .map_err(|e| {
                        redis::RedisError::from((
                            redis::ErrorKind::ClientError,
                            "Client not found",
                            e.to_string(),
                        ))
                    })?;

                // Build redis::Cmd directly from requestType int and args
                let proto_request_type = protobuf::EnumOrUnknown::<
                    glide_core::command_request::RequestType,
                >::from_i32(request_type);
                let rt: glide_core::request_type::RequestType = proto_request_type.into();
                let Some(mut cmd) = rt.get_command() else {
                    return Err(redis::RedisError::from((
                        redis::ErrorKind::ClientError,
                        "Invalid request type",
                        format!("request_type={}", request_type),
                    )));
                };
                for arg in &args_data {
                    cmd.arg(arg.as_slice());
                }

                // Apply compression
                #[allow(clippy::collapsible_if)]
                if client.is_compression_enabled() {
                    if let Err(e) = process_command_for_compression(&mut cmd, &client) {
                        if e.is_incompatible_command() {
                            return Err(redis::RedisError::from((
                                redis::ErrorKind::ClientError,
                                "Incompatible command with compression",
                                e.to_string(),
                            )));
                        }
                    }
                }

                // Compute routing
                let routing = routing::resolve_routing_from_params(
                    has_route_bool,
                    route_type_val,
                    route_param_str.as_deref(),
                    Some(&cmd),
                )
                .map_err(|e| {
                    redis::RedisError::from((
                        redis::ErrorKind::ClientError,
                        "Routing error",
                        e.to_string(),
                    ))
                })?;

                // Abandon monitor integration: for pool-borrowed clients refresh the
                // inactivity timer on every command. For blocking commands, the flag was
                // already raised synchronously on the JNI thread (pre_blocking_arc) before
                // this task was spawned, eliminating the race window between spawn() and
                // task execution where the abandon monitor could observe is_blocking=false.
                if let Some(entry) = crate::jni_pool::get_pool_client_map().get(&handle_id) {
                    glide_core::pool::refresh_client_activity(*entry.value(), handle_id);
                }

                // Use the pre-armed Arc (set synchronously before spawn). No need to
                // re-fetch or re-set here: the flag is already true if this is a blocking
                // command. The _unmark_guard (UnmarkOnDrop) will clear it on drop.

                let result = client.send_command(&mut cmd, routing).await;

                // Refresh the activity timer after a blocking command so the abandon
                // monitor does not reclaim the client immediately after completion.
                // The _unmark_guard will clear the blocking flag on drop.
                if pre_blocking_arc.is_some()
                    && let Some(entry) = crate::jni_pool::get_pool_client_map().get(&handle_id)
                {
                    glide_core::pool::refresh_client_activity(*entry.value(), handle_id);
                }

                result
            }
            .await;

            // End OpenTelemetry span if one was created
            if span_ptr != 0
                && let Ok(span) =
                    unsafe { glide_core::GlideOpenTelemetry::span_from_pointer(span_ptr as u64) }
            {
                span.end();
                unsafe {
                    std::sync::Arc::from_raw(span_ptr as *const glide_core::GlideSpan);
                }
            }

            complete_callback(jvm, callback_id, result, !expect_utf8_bool);
        });

        Some(())
    })
    .unwrap_or(())
}

/// Execute a script asynchronously using FFI-imported logic
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeScriptAsync(
    mut env: JNIEnv,
    _class: JClass,
    handle_id: jlong,
    callback_id: jlong,
    hash: JString,
    keys: jni::objects::JObjectArray,
    args: jni::objects::JObjectArray,
    has_route: jni::sys::jboolean,
    route_type: jint,
    route_param: JString,
    expect_utf8: jni::sys::jboolean,
) {
    run_ffi(|| {
        let Some(jvm) = get_jvm_or_complete_error(&mut env, callback_id, "executeScriptAsync")
        else {
            return Some(());
        };

        if let Some(entry) = crate::jni_pool::get_pool_client_map().get(&(handle_id as u64)) {
            glide_core::pool::refresh_client_activity(*entry.value(), handle_id as u64);
        }

        // Extract script hash
        let hash_str = match env.get_string(&hash) {
            Ok(h) => h.to_string_lossy().to_string(),
            Err(e) => {
                log::error!("Failed to read script hash: {e}");
                complete_callback(
                    jvm,
                    callback_id,
                    Err(redis::RedisError::from((
                        redis::ErrorKind::ClientError,
                        "Failed to read hash",
                        e.to_string(),
                    ))),
                    false,
                );
                return Some(());
            }
        };

        // Extract keys array (supports String[] or byte[][])
        let keys_vec: Result<Vec<Vec<u8>>, FFIError> = (|| {
            if keys.is_null() {
                return Ok(Vec::new());
            }
            let length = env.get_array_length(&keys)? as usize;
            let mut keys_data = Vec::with_capacity(length);

            for i in 0..length {
                let key_obj = env.get_object_array_element(&keys, i as i32)?;
                if env.is_instance_of(&key_obj, "[B")? {
                    let key_bytes = env.convert_byte_array(JByteArray::from(key_obj))?;
                    keys_data.push(key_bytes);
                } else {
                    let jstr = JString::from(key_obj);
                    let s: String = env.get_string(&jstr)?.into();
                    keys_data.push(s.into_bytes());
                }
            }
            Ok(keys_data)
        })();

        let keys_data = match keys_vec {
            Ok(k) => k,
            Err(e) => {
                log::error!("Failed to extract script keys: {e}");
                complete_callback(
                    jvm,
                    callback_id,
                    Err(redis::RedisError::from((
                        redis::ErrorKind::ClientError,
                        "Failed to extract keys",
                        e.to_string(),
                    ))),
                    false,
                );
                return Some(());
            }
        };

        // Extract args array (supports String[] or byte[][])
        let args_vec: Result<Vec<Vec<u8>>, FFIError> = (|| {
            if args.is_null() {
                return Ok(Vec::new());
            }
            let length = env.get_array_length(&args)? as usize;
            let mut args_data = Vec::with_capacity(length);

            for i in 0..length {
                let arg_obj = env.get_object_array_element(&args, i as i32)?;
                if env.is_instance_of(&arg_obj, "[B")? {
                    let arg_bytes = env.convert_byte_array(JByteArray::from(arg_obj))?;
                    args_data.push(arg_bytes);
                } else {
                    let jstr = JString::from(arg_obj);
                    let s: String = env.get_string(&jstr)?.into();
                    args_data.push(s.into_bytes());
                }
            }
            Ok(args_data)
        })();

        let args_data = match args_vec {
            Ok(a) => a,
            Err(e) => {
                log::error!("Failed to extract script args: {e}");
                complete_callback(
                    jvm,
                    callback_id,
                    Err(redis::RedisError::from((
                        redis::ErrorKind::ClientError,
                        "Failed to extract args",
                        e.to_string(),
                    ))),
                    false,
                );
                return Some(());
            }
        };

        let client_handle_id = handle_id as u64;

        // Extract route parameters on the current thread (avoid JNI env escaping into async)
        let has_route_bool = has_route != 0;
        let route_type_val: i32 = route_type;
        let route_param_str: Option<String> = if !route_param.is_null() {
            match env.get_string(&route_param) {
                Ok(s) => Some(s.into()),
                Err(_) => None,
            }
        } else {
            None
        };

        // Synchronously raise the blocking flag for scripts BEFORE spawning: scripts
        // execute synchronously on the server (EVALSHA blocks until completion), so the
        // client must be considered blocking for the duration. Raising the flag here
        // prevents the abandon monitor from reclaiming the client in the window between
        // spawn() and when the async task actually runs on a worker thread.
        // Conservative: mark blocking for the full batch/script duration regardless
        // of whether the payload contains a blocking command.
        let script_pre_blocking_arc: Option<Arc<AtomicBool>> = {
            let arc = glide_core::pool::get_blocking_flag(client_handle_id);
            if let Some(ref a) = arc {
                a.store(true, std::sync::atomic::Ordering::Release);
            }
            arc
        };

        // Spawn async task for script execution using FFI-imported patterns
        let runtime = get_runtime();
        runtime.spawn(async move {
            // RAII guard: ensures is_blocking is cleared on every exit path —
            // routing errors, client-not-found, normal completion, or cancellation.
            // Guard arms immediately on task entry — flag was already set true before spawn.
            let _unmark_guard = UnmarkOnDrop(script_pre_blocking_arc.clone());

            let client_result = ensure_client_for_handle(client_handle_id).await;
            match client_result {
                Ok(mut client) => {
                    // Determine routing: explicit route if provided, otherwise infer from keys via EVALSHA-shaped command
                    let routing_info = if has_route_bool {
                        match routing::resolve_routing_from_params(
                            true,
                            route_type_val,
                            route_param_str.as_deref(),
                            None,
                        ) {
                            Ok(r) => r,
                            Err(e) => {
                                complete_callback(
                                    jvm,
                                    callback_id,
                                    Err(redis::RedisError::from((
                                        redis::ErrorKind::ClientError,
                                        "Routing error",
                                        e.to_string(),
                                    ))),
                                    false,
                                );
                                return;
                            }
                        }
                    } else {
                        // Auto route by constructing EVALSHA-shaped command
                        let mut route_cmd = redis::cmd("EVALSHA");
                        route_cmd.arg(hash_str.as_bytes());
                        route_cmd.arg(keys_data.len());
                        for k in &keys_data {
                            route_cmd.arg(k.as_slice());
                        }
                        for a in &args_data {
                            route_cmd.arg(a.as_slice());
                        }
                        match routing::get_route(Default::default(), Some(&route_cmd)) {
                            Ok(r) => r,
                            Err(e) => {
                                complete_callback(
                                    jvm,
                                    callback_id,
                                    Err(redis::RedisError::from((
                                        redis::ErrorKind::ClientError,
                                        "Routing error",
                                        e.to_string(),
                                    ))),
                                    false,
                                );
                                return;
                            }
                        }
                    };

                    // Use the pre-armed Arc (set synchronously before spawn). The flag is
                    // already true, so no re-fetch or re-set needed. The RAII guard above
                    // ensures the flag is cleared on all exit paths.
                    let script_blocking_arc = script_pre_blocking_arc;

                    let result = client
                        .invoke_script(
                            &hash_str,
                            &keys_data.iter().map(|k| k.as_slice()).collect::<Vec<_>>(),
                            &args_data.iter().map(|a| a.as_slice()).collect::<Vec<_>>(),
                            routing_info,
                        )
                        .await
                        .map_err(|e| {
                            redis::RedisError::from((
                                redis::ErrorKind::ClientError,
                                "Script execution failed",
                                e.to_string(),
                            ))
                        });

                    // Refresh activity after script completes so the abandon monitor
                    // does not reclaim the client immediately after a long blocking script.
                    // The RAII guard handles clearing the is_blocking flag on drop.
                    if script_blocking_arc.is_some()
                        && let Some(entry) =
                            crate::jni_pool::get_pool_client_map().get(&client_handle_id)
                    {
                        glide_core::pool::refresh_client_activity(*entry.value(), client_handle_id);
                    }

                    let binary_mode = expect_utf8 == 0;
                    complete_callback(jvm, callback_id, result, binary_mode);
                }
                Err(err) => {
                    let error = Err(redis::RedisError::from((
                        redis::ErrorKind::ClientError,
                        "Client not found",
                        err.to_string(),
                    )));
                    let binary_mode = expect_utf8 == 0;
                    complete_callback(jvm, callback_id, error, binary_mode);
                }
            }
        });

        Some(())
    })
    .unwrap_or(())
}

/// Update connection password
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_updateConnectionPassword(
    mut env: JNIEnv,
    _class: JClass,
    _client_ptr: jlong,
    password: jni::sys::jstring,
    immediate_auth: jni::sys::jboolean,
    callback_id: jlong,
) {
    run_ffi(|| {
        let password_opt = get_optional_string_param_raw(&mut env, password);
        let handle_id = _client_ptr as u64;
        let do_immediate = immediate_auth != 0;

        let Some(jvm) =
            get_jvm_or_complete_error(&mut env, callback_id, "updateConnectionPassword")
        else {
            return Some(());
        };

        get_runtime().spawn(async move {
            let client_result = ensure_client_for_handle(handle_id).await;
            match client_result {
                Ok(mut client) => {
                    let result = client
                        .update_connection_password(password_opt, do_immediate)
                        .await
                        .map(|_| redis::Value::Okay)
                        .map_err(|e| {
                            redis::RedisError::from((
                                redis::ErrorKind::ClientError,
                                "Password update failed",
                                e.to_string(),
                            ))
                        });

                    complete_callback(jvm, callback_id, result, false);
                }
                Err(err) => {
                    let error = Err(redis::RedisError::from((
                        redis::ErrorKind::ClientError,
                        "Client not found",
                        err.to_string(),
                    )));
                    complete_callback(jvm, callback_id, error, false);
                }
            }
        });

        Some(())
    })
    .unwrap_or(())
}

/// Manually refresh IAM authentication token
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_refreshIamToken(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    callback_id: jlong,
) {
    run_ffi(|| {
        let handle_id = client_ptr as u64;

        let Some(jvm) = get_jvm_or_complete_error(&mut env, callback_id, "refreshIamToken") else {
            return Some(());
        };

        get_runtime().spawn(async move {
            let client_result = ensure_client_for_handle(handle_id).await;
            match client_result {
                Ok(mut client) => {
                    let result = client
                        .refresh_iam_token()
                        .await
                        .map(|_| redis::Value::Okay)
                        .map_err(|e| {
                            redis::RedisError::from((
                                redis::ErrorKind::ClientError,
                                "IAM token refresh failed",
                                e.to_string(),
                            ))
                        });
                    complete_callback(jvm, callback_id, result, false);
                }
                Err(err) => {
                    let error = Err(redis::RedisError::from((
                        redis::ErrorKind::ClientError,
                        "Client not found",
                        err.to_string(),
                    )));
                    complete_callback(jvm, callback_id, error, false);
                }
            }
        });

        Some(())
    })
    .unwrap_or(())
}

/// JNI bridge for cluster scan that properly manages cursor lifecycle
/// This reuses the existing cluster scan logic from glide-core
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeClusterScanAsync(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    cursor_id: JString,
    match_pattern: JString,
    count: jlong,
    object_type: JString,
    expect_utf8: jni::sys::jboolean,
    callback_id: jlong,
) {
    run_ffi(|| {
        let Some(jvm) = get_jvm_or_complete_error(&mut env, callback_id, "executeClusterScanAsync")
        else {
            return Some(());
        };

        // Extract cursor ID (null-safe: null means initial cursor)
        let cursor_str = if cursor_id.is_null() {
            String::new()
        } else {
            match env.get_string(&cursor_id) {
                Ok(s) => s.to_string_lossy().to_string(),
                Err(e) => {
                    log::error!("Failed to read cursor ID: {e}");
                    complete_callback(
                        jvm,
                        callback_id,
                        Err(redis::RedisError::from((
                            redis::ErrorKind::ClientError,
                            "Failed to read cursor ID",
                            e.to_string(),
                        ))),
                        false,
                    );
                    return Some(());
                }
            }
        };

        // Extract optional match pattern
        let pattern = if match_pattern.is_null() {
            None
        } else {
            match env.get_string(&match_pattern) {
                Ok(s) => Some(s.to_string_lossy().to_string()),
                Err(e) => {
                    log::error!("Failed to read match pattern: {e}");
                    complete_callback(
                        jvm,
                        callback_id,
                        Err(redis::RedisError::from((
                            redis::ErrorKind::ClientError,
                            "Failed to read match pattern",
                            e.to_string(),
                        ))),
                        false,
                    );
                    return Some(());
                }
            }
        };

        // Extract optional object type
        let obj_type = if object_type.is_null() {
            None
        } else {
            match env.get_string(&object_type) {
                Ok(s) => Some(s.to_string_lossy().to_string()),
                Err(e) => {
                    log::error!("Failed to read object type: {e}");
                    complete_callback(
                        jvm,
                        callback_id,
                        Err(redis::RedisError::from((
                            redis::ErrorKind::ClientError,
                            "Failed to read object type",
                            e.to_string(),
                        ))),
                        false,
                    );
                    return Some(());
                }
            }
        };

        let client_handle_id = client_ptr as u64;
        let count_value = if count > 0 { Some(count as u32) } else { None };

        // Spawn async task for cluster scan execution
        let runtime = get_runtime();
        runtime.spawn(async move {
            let client_result = ensure_client_for_handle(client_handle_id).await;
            match client_result {
                Ok(mut client) => {
                    // Get or create scan state cursor - using redis compatible types for now
                    let scan_state_cursor = if cursor_str.is_empty() || cursor_str == "0" {
                        // Create new initial cursor
                        redis::ScanStateRC::new()
                    } else {
                        // Get existing cursor from container
                        match glide_core::cluster_scan_container::get_cluster_scan_cursor(
                            cursor_str,
                        ) {
                            Ok(cursor) => cursor,
                            Err(e) => {
                                complete_callback(
                                    jvm,
                                    callback_id,
                                    Err(redis::RedisError::from((
                                        redis::ErrorKind::ClientError,
                                        "Invalid cursor",
                                        e.to_string(),
                                    ))),
                                    false,
                                );
                                return;
                            }
                        }
                    };

                    // Build cluster scan args
                    let mut scan_args_builder = redis::ClusterScanArgs::builder();
                    if let Some(pattern) = pattern {
                        scan_args_builder =
                            scan_args_builder.with_match_pattern::<bytes::Bytes>(pattern.into());
                    }
                    if let Some(count) = count_value {
                        scan_args_builder = scan_args_builder.with_count(count);
                    }
                    if let Some(obj_type) = obj_type {
                        scan_args_builder = scan_args_builder.with_object_type(obj_type.into());
                    }
                    let scan_args = scan_args_builder.build();

                    // Execute cluster scan
                    let result = client
                        .cluster_scan(&scan_state_cursor, scan_args)
                        .await
                        .map_err(|e| {
                            redis::RedisError::from((
                                redis::ErrorKind::ClientError,
                                "Cluster scan execution failed",
                                e.to_string(),
                            ))
                        });

                    // binary_mode = !expect_utf8
                    let binary_mode = expect_utf8 == 0;
                    complete_callback(jvm, callback_id, result, binary_mode);
                }
                Err(err) => {
                    let error = Err(redis::RedisError::from((
                        redis::ErrorKind::ClientError,
                        "Client not found",
                        err.to_string(),
                    )));
                    let binary_mode = expect_utf8 == 0;
                    complete_callback(jvm, callback_id, error, binary_mode);
                }
            }
        });

        Some(())
    })
    .unwrap_or(())
}

#[derive(Clone)]
pub struct JavaValueConversionCache {
    long_class: GlobalRef,
    long_ctor: JMethodID,
    double_class: GlobalRef,
    double_value_of: JStaticMethodID,
    boolean_class: GlobalRef,
    boolean_value_of: JStaticMethodID,
    linked_hash_map_class: GlobalRef,
    linked_hash_map_ctor: JMethodID,
    linked_hash_map_put: JMethodID,
    hash_set_class: GlobalRef,
    hash_set_ctor: JMethodID,
    hash_set_add: JMethodID,
    hash_map_class: GlobalRef,
    hash_map_ctor: JMethodID,
    hash_map_put: JMethodID,
    big_integer_class: GlobalRef,
    big_integer_ctor: JMethodID,
    request_exception_class: GlobalRef,
    request_exception_ctor: JMethodID,
}

static JAVA_VALUE_CONVERSION_CACHE: OnceLock<Mutex<Option<JavaValueConversionCache>>> =
    OnceLock::new();

fn get_java_value_conversion_cache(
    env: &mut JNIEnv,
) -> Result<&'static JavaValueConversionCache, FFIError> {
    let cache_mutex = JAVA_VALUE_CONVERSION_CACHE.get_or_init(|| Mutex::new(None));
    {
        let guard = cache_mutex.lock();
        if let Some(ref cache) = *guard {
            return Ok(unsafe {
                std::mem::transmute::<&JavaValueConversionCache, &JavaValueConversionCache>(cache)
            });
        }
    }

    let long_cls = env.find_class("java/lang/Long")?;
    let long_ctor = env.get_method_id(&long_cls, "<init>", "(J)V")?;
    let long_class = env.new_global_ref(&long_cls)?;

    let double_cls = env.find_class("java/lang/Double")?;
    let double_value_of =
        env.get_static_method_id(&double_cls, "valueOf", "(D)Ljava/lang/Double;")?;
    let double_class = env.new_global_ref(&double_cls)?;

    let boolean_cls = env.find_class("java/lang/Boolean")?;
    let boolean_value_of =
        env.get_static_method_id(&boolean_cls, "valueOf", "(Z)Ljava/lang/Boolean;")?;
    let boolean_class = env.new_global_ref(&boolean_cls)?;

    let lhm_cls = env.find_class("java/util/LinkedHashMap")?;
    let lhm_ctor = env.get_method_id(&lhm_cls, "<init>", "()V")?;
    let lhm_put = env.get_method_id(
        &lhm_cls,
        "put",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
    )?;
    let linked_hash_map_class = env.new_global_ref(&lhm_cls)?;

    let hs_cls = env.find_class("java/util/HashSet")?;
    let hs_ctor = env.get_method_id(&hs_cls, "<init>", "()V")?;
    let hs_add = env.get_method_id(&hs_cls, "add", "(Ljava/lang/Object;)Z")?;
    let hash_set_class = env.new_global_ref(&hs_cls)?;

    let hm_cls = env.find_class("java/util/HashMap")?;
    let hm_ctor = env.get_method_id(&hm_cls, "<init>", "()V")?;
    let hm_put = env.get_method_id(
        &hm_cls,
        "put",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
    )?;
    let hash_map_class = env.new_global_ref(&hm_cls)?;

    let bi_cls = env.find_class("java/math/BigInteger")?;
    let bi_ctor = env.get_method_id(&bi_cls, "<init>", "(Ljava/lang/String;)V")?;
    let big_integer_class = env.new_global_ref(&bi_cls)?;

    let req_exc_cls = env.find_class("glide/api/models/exceptions/RequestException")?;
    let req_exc_ctor = env.get_method_id(&req_exc_cls, "<init>", "(Ljava/lang/String;)V")?;
    let request_exception_class = env.new_global_ref(&req_exc_cls)?;

    let cache = JavaValueConversionCache {
        long_class,
        long_ctor,
        double_class,
        double_value_of,
        boolean_class,
        boolean_value_of,
        linked_hash_map_class,
        linked_hash_map_ctor: lhm_ctor,
        linked_hash_map_put: lhm_put,
        hash_set_class,
        hash_set_ctor: hs_ctor,
        hash_set_add: hs_add,
        hash_map_class,
        hash_map_ctor: hm_ctor,
        hash_map_put: hm_put,
        big_integer_class,
        big_integer_ctor: bi_ctor,
        request_exception_class,
        request_exception_ctor: req_exc_ctor,
    };

    // Prefer existing value if concurrently initialized
    {
        let mut guard = cache_mutex.lock();
        if guard.is_none() {
            *guard = Some(cache);
        }
    }

    let guard = cache_mutex.lock();
    let cache_ref = guard
        .as_ref()
        .expect("JavaValueConversionCache should be initialized");
    Ok(unsafe {
        std::mem::transmute::<&JavaValueConversionCache, &JavaValueConversionCache>(cache_ref)
    })
}

/// Get Java value conversion cache using correct classloader context
fn get_java_value_conversion_cache_safe(
    fallback_env: &mut JNIEnv,
) -> Result<&'static JavaValueConversionCache, FFIError> {
    // Try cached JVM env first
    if let Some(cached_jvm) = jni_client::JVM.get()
        && let Ok(mut cached_env) = cached_jvm.get_env()
    {
        return get_java_value_conversion_cache(&mut cached_env);
    }
    // Otherwise fallback to provided env
    get_java_value_conversion_cache(fallback_env)
}

/// Clean up global references in lib.rs caches
pub(crate) fn cleanup_global_caches() {
    if let Some(cache_mutex) = JAVA_VALUE_CONVERSION_CACHE.get() {
        *cache_mutex.lock() = None;
    }

    if let Some(cache_mutex) = REGISTRY_METHOD_CACHE.get() {
        *cache_mutex.lock() = None;
    }
}

fn to_local_jclass<'a>(env: &mut JNIEnv<'a>, global: &GlobalRef) -> Result<JClass<'a>, FFIError> {
    let local = env.new_local_ref(global.as_obj())?;
    Ok(JClass::from(local))
}

static OK_STRING_GLOBAL: OnceLock<GlobalRef> = OnceLock::new();

fn get_ok_jstring<'a>(env: &mut JNIEnv<'a>) -> Result<JString<'a>, FFIError> {
    if OK_STRING_GLOBAL.get().is_none() {
        let s = env.new_string("OK")?;
        let g = env.new_global_ref(&s)?;
        let _ = OK_STRING_GLOBAL.set(g);
    }
    let global = OK_STRING_GLOBAL
        .get()
        .expect("OK_STRING_GLOBAL should be initialized");
    let local = env.new_local_ref(global.as_obj())?;
    Ok(JString::from(local))
}

// ==================== MONITOR CLIENT SUPPORT ====================

static MONITOR_CLIENTS: std::sync::OnceLock<
    dashmap::DashMap<u64, glide_core::client::MonitorClient>,
> = std::sync::OnceLock::new();
static NEXT_MONITOR_ID: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);

fn get_monitor_clients() -> &'static dashmap::DashMap<u64, glide_core::client::MonitorClient> {
    MONITOR_CLIENTS.get_or_init(dashmap::DashMap::new)
}

/// Create a MONITOR client that streams all commands to a Java callback.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_createMonitorClient(
    mut env: JNIEnv,
    _class: JClass,
    connection_request_bytes: JByteArray,
    callback_object: JObject,
) -> jlong {
    run_ffi(|| {
        fn create_monitor(
            env: &mut JNIEnv,
            connection_request_bytes: JByteArray,
            callback_object: JObject,
        ) -> Result<jlong, FFIError> {
            let request_bytes = env.convert_byte_array(&connection_request_bytes)?;
            let proto_request =
                glide_core::connection_request::ConnectionRequest::parse_from_bytes(&request_bytes)
                    .map_err(|e| {
                        FFIError::Logger(format!("Failed to parse ConnectionRequest: {e}"))
                    })?;

            // Extract first address
            let addr_proto = proto_request
                .addresses
                .first()
                .ok_or_else(|| FFIError::Logger("No addresses in ConnectionRequest".to_string()))?;
            let address = glide_core::client::NodeAddress {
                host: addr_proto.host.to_string(),
                port: addr_proto.port as u16,
            };

            let lib_name = if proto_request.lib_name.is_empty() {
                None
            } else {
                Some(proto_request.lib_name.to_string())
            };

            // Build RedisConnectionInfo from protobuf auth fields
            let redis_connection_info =
                if let Some(auth) = proto_request.authentication_info.as_ref() {
                    redis::RedisConnectionInfo {
                        db: proto_request.database_id as i64,
                        username: if auth.username.is_empty() {
                            None
                        } else {
                            Some(auth.username.to_string())
                        },
                        password: if auth.password.is_empty() {
                            None
                        } else {
                            Some(auth.password.to_string())
                        },
                        protocol: match proto_request.protocol.enum_value_or_default() {
                            glide_core::connection_request::ProtocolVersion::RESP3 => {
                                redis::ProtocolVersion::RESP3
                            }
                            _ => redis::ProtocolVersion::RESP2,
                        },
                        client_name: None,
                        lib_name: lib_name.clone(),
                        lib_ver: None,
                        cache: None,
                        server_assisted_cache: false,
                    }
                } else {
                    redis::RedisConnectionInfo {
                        db: proto_request.database_id as i64,
                        username: None,
                        password: None,
                        protocol: match proto_request.protocol.enum_value_or_default() {
                            glide_core::connection_request::ProtocolVersion::RESP3 => {
                                redis::ProtocolVersion::RESP3
                            }
                            _ => redis::ProtocolVersion::RESP2,
                        },
                        client_name: None,
                        lib_name,
                        lib_ver: None,
                        cache: None,
                        server_assisted_cache: false,
                    }
                };

            // TLS mode
            let tls_mode = match proto_request.tls_mode.enum_value_or_default() {
                glide_core::connection_request::TlsMode::SecureTls => {
                    glide_core::client::TlsMode::SecureTls
                }
                glide_core::connection_request::TlsMode::InsecureTls => {
                    glide_core::client::TlsMode::InsecureTls
                }
                _ => glide_core::client::TlsMode::NoTls,
            };

            // Cache JVM if not already cached
            if let Ok(jvm) = env.get_java_vm() {
                let _ = jni_client::JVM.set(Arc::new(jvm));
            }

            // Make a global ref so the callback object outlives this stack frame
            let callback_global = env.new_global_ref(&callback_object)?;

            let on_line: glide_core::client::MonitorLineCallback =
                Arc::new(move |line: glide_core::client::MonitorLine| {
                    let Some(jvm) = jni_client::JVM.get() else {
                        log::warn!("MonitorClient callback: JVM not initialized, dropping message");
                        return;
                    };
                    let Ok(mut cb_env) = jvm.attach_current_thread_as_daemon() else {
                        return;
                    };
                    let args_json =
                        serde_json::to_string(&line.args).unwrap_or_else(|_| "[]".to_string());
                    let Ok(j_client_addr) = cb_env.new_string(&line.client_addr) else {
                        return;
                    };
                    let Ok(j_command) = cb_env.new_string(&line.command) else {
                        return;
                    };
                    let Ok(j_args_json) = cb_env.new_string(&args_json) else {
                        return;
                    };
                    let result = cb_env.call_method(
                        &callback_global,
                        "onMonitorMessage",
                        "(DJLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                        &[
                            jni::objects::JValue::Double(line.timestamp),
                            jni::objects::JValue::Long(line.db),
                            jni::objects::JValue::Object(&j_client_addr),
                            jni::objects::JValue::Object(&j_command),
                            jni::objects::JValue::Object(&j_args_json),
                        ],
                    );
                    if let Err(e) = result {
                        log::warn!("MonitorClient: JNI callback failed: {e}");
                        let _ = cb_env.exception_clear();
                    }
                });

            let monitor = jni_client::get_runtime()
                .block_on(glide_core::client::MonitorClient::new(
                    &address,
                    redis_connection_info,
                    tls_mode,
                    on_line,
                ))
                .map_err(|e| FFIError::Logger(format!("Failed to create MonitorClient: {e}")))?;

            let id = NEXT_MONITOR_ID.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
            get_monitor_clients().insert(id, monitor);
            Ok(id as jlong)
        }

        let result = create_monitor(&mut env, connection_request_bytes, callback_object);
        handle_errors(&mut env, result)
    })
    .unwrap_or(0)
}

/// Close a MONITOR client by ID (drops it, stopping the monitor stream).
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_closeMonitorClient(
    _env: JNIEnv,
    _class: JClass,
    monitor_id: jlong,
) {
    get_monitor_clients().remove(&(monitor_id as u64));
}

/// Get cache metrics asynchronously
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getCacheMetrics(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    callback_id: jlong,
    metrics_type: jint,
) {
    run_ffi(|| {
        let handle_id = client_ptr as u64;

        let Some(jvm) = get_jvm_or_complete_error(&mut env, callback_id, "getCacheMetrics") else {
            return Some(());
        };

        get_runtime().spawn(async move {
            let client_result = ensure_client_for_handle(handle_id).await;
            match client_result {
                Ok(client) => {
                    use glide_core::command_request::CacheMetricsType;
                    use protobuf::Enum;

                    let result = match CacheMetricsType::from_i32(metrics_type) {
                        Some(CacheMetricsType::HitRate) => client.cache_hit_rate(),
                        Some(CacheMetricsType::MissRate) => client.cache_miss_rate(),
                        Some(CacheMetricsType::EntryCount) => client.cache_entry_count(),
                        Some(CacheMetricsType::Evictions) => client.cache_evictions(),
                        Some(CacheMetricsType::Expirations) => client.cache_expirations(),
                        Some(CacheMetricsType::TotalLookups) => client.cache_total_lookups(),
                        None => Err(redis::RedisError::from((
                            redis::ErrorKind::ClientError,
                            "Invalid cache metrics type",
                            format!("Unknown metrics type: {}", metrics_type),
                        ))),
                    };

                    let final_result = result.map_err(|e| {
                        redis::RedisError::from((
                            redis::ErrorKind::ClientError,
                            "Cache metrics error",
                            e.to_string(),
                        ))
                    });

                    complete_callback(jvm, callback_id, final_result, false);
                }
                Err(err) => {
                    let error = Err(redis::RedisError::from((
                        redis::ErrorKind::ClientError,
                        "Client not found",
                        err.to_string(),
                    )));
                    complete_callback(jvm, callback_id, error, false);
                }
            }
        });

        Some(())
    })
    .unwrap_or(())
}
