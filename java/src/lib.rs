/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use glide_core::start_socket_listener as start_socket_listener_core;
use glide_core::MAX_REQUEST_ARGS_LENGTH as MAX_REQUEST_ARGS_LENGTH_IN_BYTES;

use bytes::Bytes;
use jni::objects::{JByteArray, JClass, JObject, JObjectArray, JString};
use jni::sys::{jlong, jsize};
use jni::JNIEnv;
use redis::Value;
use std::sync::mpsc;

mod errors;

use errors::{handle_errors, handle_panics, FFIError};

#[cfg(ffi_test)]
mod ffi_test;
#[cfg(ffi_test)]
pub use ffi_test::*;

struct Level(i32);

// TODO: Consider caching method IDs here in a static variable (might need RwLock to mutate)
fn redis_value_to_java<'local>(
    env: &mut JNIEnv<'local>,
    val: Value,
    encoding_utf8: bool,
) -> Result<JObject<'local>, FFIError> {
    match val {
        Value::Nil => Ok(JObject::null()),
        Value::SimpleString(str) => Ok(JObject::from(env.new_string(str)?)),
        Value::Okay => Ok(JObject::from(env.new_string("OK")?)),
        Value::Int(num) => Ok(env.new_object("java/lang/Long", "(J)V", &[num.into()])?),
        Value::BulkString(data) => {
            if encoding_utf8 {
                let utf8_str = String::from_utf8(data)?;
                Ok(JObject::from(env.new_string(utf8_str)?))
            } else {
                Ok(JObject::from(env.byte_array_from_slice(&data)?))
            }
        }
        Value::Array(array) => {
            let items: JObjectArray =
                env.new_object_array(array.len() as i32, "java/lang/Object", JObject::null())?;

            for (i, item) in array.into_iter().enumerate() {
                let java_value = redis_value_to_java(env, item, encoding_utf8)?;
                env.set_object_array_element(&items, i as i32, java_value)?;
            }

            Ok(items.into())
        }
        Value::Map(map) => {
            let linked_hash_map = env.new_object("java/util/LinkedHashMap", "()V", &[])?;

            for (key, value) in map {
                let java_key = redis_value_to_java(env, key, encoding_utf8)?;
                let java_value = redis_value_to_java(env, value, encoding_utf8)?;
                env.call_method(
                    &linked_hash_map,
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    &[(&java_key).into(), (&java_value).into()],
                )?;
            }

            Ok(linked_hash_map)
        }
        Value::Double(float) => Ok(env.new_object("java/lang/Double", "(D)V", &[float.into()])?),
        Value::Boolean(bool) => Ok(env.new_object("java/lang/Boolean", "(Z)V", &[bool.into()])?),
        Value::VerbatimString { format: _, text } => Ok(JObject::from(env.new_string(text)?)),
        Value::BigNumber(_num) => todo!(),
        Value::Set(array) => {
            let set = env.new_object("java/util/HashSet", "()V", &[])?;

            for elem in array {
                let java_value = redis_value_to_java(env, elem, encoding_utf8)?;
                env.call_method(
                    &set,
                    "add",
                    "(Ljava/lang/Object;)Z",
                    &[(&java_value).into()],
                )?;
            }

            Ok(set)
        }
        Value::Attribute {
            data: _,
            attributes: _,
        } => todo!(),
        Value::Push { kind: _, data: _ } => todo!(),
    }
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_valueFromPointer<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn value_from_pointer<'a>(
                env: &mut JNIEnv<'a>,
                pointer: jlong,
            ) -> Result<JObject<'a>, FFIError> {
                let value = unsafe { Box::from_raw(pointer as *mut Value) };
                redis_value_to_java(env, *value, true)
            }
            let result = value_from_pointer(&mut env, pointer);
            handle_errors(&mut env, result)
        },
        "valueFromPointer",
    )
    .unwrap_or(JObject::null())
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_valueFromPointerBinary<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn value_from_pointer_binary<'a>(
                env: &mut JNIEnv<'a>,
                pointer: jlong,
            ) -> Result<JObject<'a>, FFIError> {
                let value = unsafe { Box::from_raw(pointer as *mut Value) };
                redis_value_to_java(env, *value, false)
            }
            let result = value_from_pointer_binary(&mut env, pointer);
            handle_errors(&mut env, result)
        },
        "valueFromPointerBinary",
    )
    .unwrap_or(JObject::null())
}

/// Creates a leaked vector of byte arrays representing the args and returns a handle to it.
///
/// This function is meant to be invoked by Java using JNI.
///
/// * `env`     - The JNI environment.
/// * `_class`  - The class object. Not used.
/// * `args`    - The arguments. This should be a byte[][] from Java.
#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_createLeakedBytesVec<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    args: JObjectArray<'local>,
) -> jlong {
    handle_panics(
        move || {
            fn create_leaked_bytes_vec<'a>(
                env: &mut JNIEnv<'a>,
                args: JObjectArray<'a>,
            ) -> Result<jlong, FFIError> {
                let num_elements = env.get_array_length(&args)?;
                let mut bytes_vec = Vec::with_capacity(num_elements as usize);

                for index in 0..num_elements {
                    let value = env.get_object_array_element(&args, index as jsize)?;
                    bytes_vec.push(Bytes::from(
                        env.convert_byte_array(JByteArray::from(value))?,
                    ))
                }
                Ok(Box::leak(Box::new(bytes_vec)) as *mut Vec<Bytes> as jlong)
            }
            let result = create_leaked_bytes_vec(&mut env, args);
            handle_errors(&mut env, result)
        },
        "createLeakedBytesVec",
    )
    .unwrap_or(0)
}

/// Returns the maximum total length in bytes of request arguments.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `_env`    - The JNI environment. Not used.
/// * `_class`  - The class object. Not used.
#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_getMaxRequestArgsLengthInBytes<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    MAX_REQUEST_ARGS_LENGTH_IN_BYTES as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_SocketListenerResolver_startSocketListener<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn start_socket_listener<'a>(env: &mut JNIEnv<'a>) -> Result<JObject<'a>, FFIError> {
                let (tx, rx) = mpsc::channel::<Result<String, String>>();

                start_socket_listener_core(move |socket_path: Result<String, String>| {
                    // Signals that thread has started
                    let _ = tx.send(socket_path);
                });

                // Wait until the thread has started
                let socket_path = rx.recv();

                match socket_path {
                    Ok(Ok(path)) => env
                        .new_string(path)
                        .map(|p| p.into())
                        .map_err(|err| FFIError::Uds(err.to_string())),
                    Ok(Err(error_message)) => Err(FFIError::Uds(error_message)),
                    Err(error) => Err(FFIError::Uds(error.to_string())),
                }
            }
            let result = start_socket_listener(&mut env);
            handle_errors(&mut env, result)
        },
        "startSocketListener",
    )
    .unwrap_or(JObject::null())
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_storeScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    code: JString,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn store_script<'a>(
                env: &mut JNIEnv<'a>,
                code: JString,
            ) -> Result<JObject<'a>, FFIError> {
                let code_str: String = env.get_string(&code)?.into();
                let hash = glide_core::scripts_container::add_script(&code_str);
                Ok(JObject::from(env.new_string(hash)?))
            }
            let result = store_script(&mut env, code);
            handle_errors(&mut env, result)
        },
        "storeScript",
    )
    .unwrap_or(JObject::null())
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_dropScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    hash: JString,
) {
    handle_panics(
        move || {
            fn drop_script(env: &mut JNIEnv<'_>, hash: JString) -> Result<(), FFIError> {
                let hash_str: String = env.get_string(&hash)?.into();
                glide_core::scripts_container::remove_script(&hash_str);
                Ok(())
            }
            let result = drop_script(&mut env, hash);
            handle_errors(&mut env, result)
        },
        "dropScript",
    )
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
        }
    }
}

impl TryFrom<Level> for logger_core::Level {
    type Error = String;
    fn try_from(level: Level) -> Result<Self, <logger_core::Level as TryFrom<Level>>::Error> {
        match level.0 {
            0 => Ok(logger_core::Level::Error),
            1 => Ok(logger_core::Level::Warn),
            2 => Ok(logger_core::Level::Info),
            3 => Ok(logger_core::Level::Debug),
            4 => Ok(logger_core::Level::Trace),
            _ => Err(format!("Invalid log level: {:?}", level.0)),
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_LoggerResolver_logInternal<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    level: jint,
    log_identifier: JString<'local>,
    message: JString<'local>,
) {
    handle_panics(
        move || {
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
        },
        "logInternal",
    )
    .unwrap_or(())
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_LoggerResolver_initInternal<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    level: jint,
    file_name: JString<'local>,
) -> jint {
    handle_panics(
        move || {
            fn init_internal(level: jint, file_name: JString<'_>) -> Result<jint, FFIError> {
                let level = if level >= 0 { Some(level) } else { None };
                let file_name: Option<String> = match env.get_string(&file_name) {
                    Ok(file_name) => Some(file_name.into()),
                    Err(JniError::NullPtr(_)) => None,
                    Err(err) => return err,
                };
                let logger_level = logger_core::init(
                    level.map(|level| Level(level).try_into()?),
                    file_name.as_deref(),
                );
                Ok(Level::from(logger_level).0)
            }
            let result = init_internal(level, file_name);
            handle_errors(&mut env, result)
        },
        "initInternal",
    )
    .unwrap_or(0)
}
