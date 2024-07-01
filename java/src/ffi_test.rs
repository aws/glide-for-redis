/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use crate::errors::{handle_errors, handle_panics, throw_java_exception, ExceptionType, FFIError};
use jni::{
    objects::{JByteArray, JClass, JLongArray, JString},
    sys::{jboolean, jdouble, jlong},
    JNIEnv,
};
use redis::Value;

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedNil<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    let redis_value = Value::Nil;
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedSimpleString<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jlong {
    let value: String = env.get_string(&value).unwrap().into();
    let redis_value = Value::SimpleString(value);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedOkay<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    let redis_value = Value::Okay;
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedInt<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jlong,
) -> jlong {
    let redis_value = Value::Int(value);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedBulkString<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JByteArray<'local>,
) -> jlong {
    let value = env.convert_byte_array(&value).unwrap();
    let value = value.into_iter().collect::<Vec<u8>>();
    let redis_value = Value::BulkString(value);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedLongArray<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JLongArray<'local>,
) -> jlong {
    let array = java_long_array_to_value(&mut env, &value);
    let redis_value = Value::Array(array);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedMap<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    keys: JLongArray<'local>,
    values: JLongArray<'local>,
) -> jlong {
    let keys_vec = java_long_array_to_value(&mut env, &keys);
    let values_vec = java_long_array_to_value(&mut env, &values);
    let map: Vec<(Value, Value)> = keys_vec.into_iter().zip(values_vec).collect();
    let redis_value = Value::Map(map);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedDouble<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jdouble,
) -> jlong {
    let redis_value = Value::Double(value.into());
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedBoolean<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jboolean,
) -> jlong {
    let redis_value = Value::Boolean(value != 0);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedVerbatimString<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jlong {
    use redis::VerbatimFormat;
    let value: String = env.get_string(&value).unwrap().into();
    let redis_value = Value::VerbatimString {
        format: VerbatimFormat::Text,
        text: value,
    };
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedLongSet<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JLongArray<'local>,
) -> jlong {
    let set = java_long_array_to_value(&mut env, &value);
    let redis_value = Value::Set(set);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

fn java_long_array_to_value<'local>(
    env: &mut JNIEnv<'local>,
    array: &JLongArray<'local>,
) -> Vec<Value> {
    use jni::objects::ReleaseMode;
    let elements = unsafe {
        env.get_array_elements(array, ReleaseMode::NoCopyBack)
            .unwrap()
    };
    elements
        .iter()
        .map(|value| Value::Int(*value))
        .collect::<Vec<Value>>()
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_handlePanics<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    should_panic: jboolean,
    error_present: jboolean,
    value: jlong,
    default_value: jlong,
) -> jlong {
    let should_panic = should_panic != 0;
    let error_present = error_present != 0;
    handle_panics(
        || {
            if should_panic {
                panic!("Panicking")
            } else if error_present {
                None
            } else {
                Some(value)
            }
        },
        "handlePanics",
    )
    .unwrap_or(default_value)
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_handleErrors<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    is_success: jboolean,
    value: jlong,
    default_value: jlong,
) -> jlong {
    let is_success = is_success != 0;
    let error = FFIError::Uds("Error starting socket listener".to_string());
    let result = if is_success { Ok(value) } else { Err(error) };
    handle_errors(&mut env, result).unwrap_or(default_value)
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_throwException<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    throw_twice: jboolean,
    is_runtime_exception: jboolean,
    message: JString<'local>,
) {
    let throw_twice = throw_twice != 0;
    let is_runtime_exception = is_runtime_exception != 0;

    let exception_type = if is_runtime_exception {
        ExceptionType::RuntimeException
    } else {
        ExceptionType::Exception
    };

    let message: String = env.get_string(&message).unwrap().into();
    throw_java_exception(&mut env, exception_type, &message);

    if throw_twice {
        throw_java_exception(&mut env, exception_type, &message);
    }
}
