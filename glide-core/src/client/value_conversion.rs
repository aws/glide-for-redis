/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use redis::{
    cluster_routing::Routable, from_owned_redis_value, Cmd, ErrorKind, RedisResult, Value,
};

#[derive(Clone, Copy)]
pub(crate) enum ExpectedReturnType<'a> {
    Map {
        key_type: &'a Option<ExpectedReturnType<'a>>,
        value_type: &'a Option<ExpectedReturnType<'a>>,
    },
    MapOfStringToDouble,
    Double,
    Boolean,
    BulkString,
    Set,
    DoubleOrNull,
    ZRankReturnType,
    JsonToggleReturnType,
    ArrayOfStrings,
    ArrayOfBools,
    ArrayOfDoubleOrNull,
    Lolwut,
    ArrayOfStringAndArrays,
    ArrayOfArraysOfDoubleOrNull,
    ArrayOfMaps(&'a ExpectedReturnType<'a>),
    StringOrSet,
    ArrayOfPairs,
    ArrayOfMemberScorePairs,
    ZMPopReturnType,
    KeyWithMemberAndScore,
    FunctionStatsReturnType,
    GeoSearchReturnType,
    SimpleString,
    XAutoClaimReturnType,
}

pub(crate) fn convert_to_expected_type(
    value: Value,
    expected: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    let Some(expected) = expected else {
        return Ok(value);
    };

    match expected {
        ExpectedReturnType::Map {
            key_type,
            value_type,
        } => match value {
            Value::Nil => Ok(value),
            Value::Map(map) => convert_inner_map_by_type(map, *key_type, *value_type),
            Value::Array(array) => convert_array_to_map_by_type(array, *key_type, *value_type),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to map",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        ExpectedReturnType::MapOfStringToDouble => match value {
            Value::Nil => Ok(value),
            Value::Map(map) => {
                let result = map
                    .into_iter()
                    .map(|(key, inner_value)| {
                        let key_str = match key {
                            Value::BulkString(_) => key,
                            _ => Value::BulkString(from_owned_redis_value::<String>(key)?.into()),
                        };
                        match inner_value {
                            Value::BulkString(_) => Ok((
                                key_str,
                                Value::Double(from_owned_redis_value::<f64>(inner_value)?),
                            )),
                            Value::Double(_) => Ok((key_str, inner_value)),
                            _ => Err((
                                ErrorKind::TypeError,
                                "Response couldn't be converted to map of {string: double}",
                                format!("(response was {:?})", get_value_type(&inner_value)),
                            )
                                .into()),
                        }
                    })
                    .collect::<RedisResult<_>>();

                result.map(Value::Map)
            }
            Value::Array(array) => convert_array_to_map_by_type(
                array,
                Some(ExpectedReturnType::BulkString),
                Some(ExpectedReturnType::Double),
            ),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to map of {string: double}",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        ExpectedReturnType::Set => match value {
            Value::Nil => Ok(value),
            Value::Set(_) => Ok(value),
            Value::Array(array) => Ok(Value::Set(array)),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to set",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        ExpectedReturnType::Double => Ok(Value::Double(from_owned_redis_value::<f64>(value)?)),
        ExpectedReturnType::Boolean => Ok(Value::Boolean(from_owned_redis_value::<bool>(value)?)),
        ExpectedReturnType::DoubleOrNull => match value {
            Value::Nil => Ok(value),
            _ => Ok(Value::Double(from_owned_redis_value::<f64>(value)?)),
        },
        ExpectedReturnType::ZRankReturnType => match value {
            Value::Nil => Ok(value),
            Value::Array(mut array) => {
                if array.len() != 2 {
                    return Err((
                        ErrorKind::TypeError,
                        "Array must contain exactly two elements",
                    )
                        .into());
                }

                array[1] =
                    convert_to_expected_type(array[1].clone(), Some(ExpectedReturnType::Double))?;

                Ok(Value::Array(array))
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to Array (ZRankResponseType)",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        ExpectedReturnType::BulkString => Ok(Value::BulkString(
            from_owned_redis_value::<String>(value)?.into(),
        )),
        ExpectedReturnType::SimpleString => Ok(Value::SimpleString(
            from_owned_redis_value::<String>(value)?,
        )),
        ExpectedReturnType::JsonToggleReturnType => match value {
            Value::Array(array) => {
                let converted_array: RedisResult<Vec<_>> = array
                    .into_iter()
                    .map(|item| match item {
                        Value::Nil => Ok(Value::Nil),
                        _ => match from_owned_redis_value::<bool>(item.clone()) {
                            Ok(boolean_value) => Ok(Value::Boolean(boolean_value)),
                            _ => Err((
                                ErrorKind::TypeError,
                                "Could not convert value to boolean",
                                format!("(response was {:?})", get_value_type(&item)),
                            )
                                .into()),
                        },
                    })
                    .collect();

                converted_array.map(Value::Array)
            }
            Value::BulkString(ref bytes) => match std::str::from_utf8(bytes) {
                Ok("true") => Ok(Value::Boolean(true)),
                Ok("false") => Ok(Value::Boolean(false)),
                _ => Err((
                    ErrorKind::TypeError,
                    "Response couldn't be converted to boolean",
                    format!("(response was {:?})", get_value_type(&value)),
                )
                    .into()),
            },
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to Json Toggle return type",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        ExpectedReturnType::ArrayOfBools => match value {
            Value::Array(array) => convert_array_elements(array, ExpectedReturnType::Boolean),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to an array of boolean",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        ExpectedReturnType::ArrayOfStrings => match value {
            Value::Array(array) => convert_array_elements(array, ExpectedReturnType::BulkString),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to an array of bulk strings",
            )
                .into()),
        },
        ExpectedReturnType::ArrayOfDoubleOrNull => match value {
            Value::Array(array) => convert_array_elements(array, ExpectedReturnType::DoubleOrNull),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to an array of doubles",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        // command returns nil or an array of 2 elements, where the second element is a map represented by a 2D array
        // we convert that second element to a map as we do in `MapOfStringToDouble`
        /*
        > zmpop 1 z1 min count 10
        1) "z1"
        2) 1) 1) "2"
              2) (double) 2
           2) 1) "3"
              2) (double) 3
         */
        ExpectedReturnType::ZMPopReturnType => match value {
            Value::Nil => Ok(value),
            Value::Array(array) if array.len() == 2 && matches!(array[1], Value::Array(_)) => {
                let Value::Array(nested_array) = array[1].clone() else {
                    unreachable!("Pattern match above ensures that it is Array")
                };
                // convert the nested array to a map
                let map = convert_array_to_map_by_type(
                    nested_array,
                    Some(ExpectedReturnType::BulkString),
                    Some(ExpectedReturnType::Double),
                )?;
                Ok(Value::Array(vec![array[0].clone(), map]))
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to ZMPOP return type",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        ExpectedReturnType::ArrayOfArraysOfDoubleOrNull => match value {
            // This is used for GEOPOS command.
            Value::Array(array) => {
                let converted_array: RedisResult<Vec<_>> = array
                    .clone()
                    .into_iter()
                    .map(|item| match item {
                        Value::Nil => Ok(Value::Nil),
                        Value::Array(mut inner_array) => {
                            if inner_array.len() != 2 {
                                return Err((
                                    ErrorKind::TypeError,
                                    "Inner Array must contain exactly two elements",
                                )
                                    .into());
                            }
                            inner_array[0] = convert_to_expected_type(
                                inner_array[0].clone(),
                                Some(ExpectedReturnType::Double),
                            )?;
                            inner_array[1] = convert_to_expected_type(
                                inner_array[1].clone(),
                                Some(ExpectedReturnType::Double),
                            )?;

                            Ok(Value::Array(inner_array))
                        }
                        _ => Err((
                            ErrorKind::TypeError,
                            "Response couldn't be converted to an array of array of double or null. Inner value of Array must be Array or Null",
                            format!("(response was {:?})", get_value_type(&item)),
                        )
                            .into()),
                    })
                    .collect();

                converted_array.map(Value::Array)
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to an array of array of double or null",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        ExpectedReturnType::Lolwut => {
            match value {
                // cluster (multi-node) response - go recursive
                Value::Map(map) => convert_map_entries(
                    map,
                    Some(ExpectedReturnType::BulkString),
                    Some(ExpectedReturnType::Lolwut),
                ),
                // RESP 2 response
                Value::BulkString(bytes) => {
                    let text = std::str::from_utf8(&bytes).unwrap();
                    let res = convert_lolwut_string(text);
                    Ok(Value::BulkString(Vec::from(res)))
                }
                // RESP 3 response
                Value::VerbatimString {
                    format: _,
                    ref text,
                } => {
                    let res = convert_lolwut_string(text);
                    Ok(Value::BulkString(Vec::from(res)))
                }
                _ => Err((
                    ErrorKind::TypeError,
                    "LOLWUT response couldn't be converted to a user-friendly format",
                    format!("(response was {:?})", get_value_type(&value)),
                )
                    .into()),
            }
        }
        // Used by HRANDFIELD when the WITHVALUES arg is passed.
        // The server response can be an empty array, a flat array of key-value pairs, or a two-dimensional array of key-value pairs.
        // The conversions we do here are as follows:
        //
        // - if the server returned an empty array, return an empty array
        // - if the server returned a flat array of key-value pairs, convert to a two-dimensional array of key-value pairs
        // - if the server returned a two-dimensional array of key-value pairs, return as-is
        ExpectedReturnType::ArrayOfPairs => convert_to_array_of_pairs(value, None),
        // Used by ZRANDMEMBER when the WITHSCORES arg is passed.
        // The server response can be an empty array, a flat array of member-score pairs, or a two-dimensional array of member-score pairs.
        // The server response scores can be strings or doubles. The conversions we do here are as follows:
        //
        // - if the server returned an empty array, return an empty array
        // - if the server returned a flat array of member-score pairs, convert to a two-dimensional array of member-score pairs. The scores are converted from type string to type double.
        // - if the server returned a two-dimensional array of key-value pairs, return as-is. The scores will already be of type double since this is a RESP3 response.
        ExpectedReturnType::ArrayOfMemberScorePairs => {
            // RESP2 returns scores as strings, but we want scores as type double.
            convert_to_array_of_pairs(value, Some(ExpectedReturnType::Double))
        }
        // Used by LMPOP and BLMPOP
        // The server response can be an array or null
        //
        // Example:
        // let input = ["key", "val1", "val2"]
        // let expected =("key", vec!["val1", "val2"])
        ExpectedReturnType::ArrayOfStringAndArrays => match value {
            Value::Nil => Ok(value),
            Value::Array(array) if array.len() == 2 && matches!(array[1], Value::Array(_)) => {
                // convert the array to a map of string to string-array
                let map = convert_array_to_map_by_type(
                    array,
                    Some(ExpectedReturnType::BulkString),
                    Some(ExpectedReturnType::ArrayOfStrings),
                )?;
                Ok(map)
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to a pair of String/String-Array return type",
            )
                .into()),
        },
        // Used by BZPOPMIN/BZPOPMAX, which return an array consisting of the key of the sorted set that was popped, the popped member, and its score.
        // RESP2 returns the score as a string, but RESP3 returns the score as a double. Here we convert string scores into type double.
        ExpectedReturnType::KeyWithMemberAndScore => match value {
            Value::Nil => Ok(value),
            Value::Array(ref array) if array.len() == 3 && matches!(array[2], Value::Double(_)) => {
                Ok(value)
            }
            Value::Array(mut array)
                if array.len() == 3
                    && matches!(array[2], Value::BulkString(_) | Value::SimpleString(_)) =>
            {
                array[2] =
                    convert_to_expected_type(array[2].clone(), Some(ExpectedReturnType::Double))?;
                Ok(Value::Array(array))
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to an array containing a key, member, and score",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        // Used by GEOSEARCH.
        // When all options are specified (withcoord, withdist, withhash) , the response looks like this: [[name (str), [dist (str), hash (int), [lon (str), lat (str)]]]] for RESP2.
        // RESP3 return type is: [[name (str), [dist (str), hash (int), [lon (float), lat (float)]]]].
        // We also want to convert dist into float.
        /* from this:
        > GEOSEARCH Sicily FROMLONLAT 15 37 BYBOX 400 400 km ASC WITHCOORD WITHDIST WITHHASH
        1) 1) "Catania"
            2) "56.4413"
            3) (integer) 3479447370796909
            4) 1) "15.08726745843887329"
                2) "37.50266842333162032"
        to this:
        > GEOSEARCH Sicily FROMLONLAT 15 37 BYBOX 400 400 km ASC WITHCOORD WITHDIST WITHHASH
        1) 1) "Catania"
            2) (double) 56.4413
            3) (integer) 3479447370796909
            4) 1) (double) 15.08726745843887329
                2) (double) 37.50266842333162032
         */
        ExpectedReturnType::GeoSearchReturnType => match value {
            Value::Array(array) => {
                let mut converted_array = Vec::with_capacity(array.len());
                for item in &array {
                    if let Value::Array(inner_array) = item {
                        if let Some((name, rest)) = inner_array.split_first() {
                            let rest = rest.iter().map(|v| {
                                match v {
                                    Value::Array(coord) => {
                                        // This is the [lon (str), lat (str)] that should be converted into [lon (float), lat (float)].
                                        if coord.len() != 2 {
                                            Err((
                                                ErrorKind::TypeError,
                                                "Inner Array must contain exactly two elements, longitude and latitude",
                                            ).into())
                                        } else {
                                            coord.iter()
                                                .map(|elem| convert_to_expected_type(elem.clone(), Some(ExpectedReturnType::Double)))
                                                .collect::<Result<Vec<_>, _>>()
                                                .map(Value::Array)
                                        }
                                    }
                                    Value::BulkString(dist) => {
                                        // This is the conversion of dist from string to float
                                        convert_to_expected_type(
                                            Value::BulkString(dist.clone()),
                                            Some(ExpectedReturnType::Double),
                                        )
                                    }
                                    _ => Ok(v.clone()), // Hash is both integer for RESP2/3
                                }
                            }).collect::<Result<Vec<Value>, _>>()?;

                            converted_array
                                .push(Value::Array(vec![name.clone(), Value::Array(rest)]));
                        } else {
                            return Err((
                                ErrorKind::TypeError,
                                "Response couldn't be converted to GeoSeatch return type, Inner Array must contain at least one element",
                            )
                                .into());
                        }
                    } else {
                        return Err((
                            ErrorKind::TypeError,
                            "Response couldn't be converted to GeoSeatch return type, Expected an array as an inner element",
                        )
                            .into());
                    }
                }
                Ok(Value::Array(converted_array))
            }

            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to GeoSeatch return type, Expected an array as the outer elemen.",
            )
                .into()),
        },
        // `FUNCTION LIST` returns an array of maps with nested list of maps.
        // In RESP2 these maps are represented by arrays - we're going to convert them.
        /* RESP2 response
        1) 1) "library_name"
           2) "mylib1"
           3) "engine"
           4) "LUA"
           5) "functions"
           6) 1) 1) "name"
                 2) "myfunc1"
                 3) "description"
                 4) (nil)
                 5) "flags"
                 6) (empty array)
              2) 1) "name"
                 ...
        2) 1) "library_name"
           ...

        RESP3 response
        1) 1# "library_name" => "mylib1"
           2# "engine" => "LUA"
           3# "functions" =>
              1) 1# "name" => "myfunc1"
                 2# "description" => (nil)
                 3# "flags" => (empty set)
              2) 1# "name" => "myfunc2"
                 ...
        2) 1# "library_name" => "mylib2"
           ...
        */
        ExpectedReturnType::ArrayOfMaps(type_of_map_values) => match value {
            // empty array, or it is already contains a map (RESP3 response) - no conversion needed
            Value::Array(ref array) if array.is_empty() || matches!(array[0], Value::Map(_)) => {
                Ok(value)
            }
            Value::Array(array) => convert_array_of_flat_maps(array, Some(*type_of_map_values)),
            // cluster (multi-node) response - go recursive
            Value::Map(map) => convert_map_entries(
                map,
                Some(ExpectedReturnType::BulkString),
                Some(ExpectedReturnType::ArrayOfMaps(type_of_map_values)),
            ),
            // Due to recursion, this will convert every map value, including simple strings, which we do nothing with
            Value::BulkString(_) | Value::SimpleString(_) | Value::VerbatimString { .. } => {
                Ok(value)
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        // Not used for a command, but used as a helper for `FUNCTION LIST` to process the inner map.
        // It may contain a string (name, description) or set (flags), or nil (description).
        // The set is stored as array in RESP2. See example for `ArrayOfMaps` above.
        ExpectedReturnType::StringOrSet => match value {
            Value::Array(_) => convert_to_expected_type(value, Some(ExpectedReturnType::Set)),
            Value::Nil
            | Value::BulkString(_)
            | Value::SimpleString(_)
            | Value::VerbatimString { .. } => Ok(value),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
        // `FUNCTION STATS` returns nested maps with different types of data
        /* RESP2 response example
        1) "running_script"
        2) 1) "name"
           2) "<function name>"
           3) "command"
           4) 1) "fcall"
              2) "<function name>"
              ... rest `fcall` args ...
           5) "duration_ms"
           6) (integer) 24529
        3) "engines"
        4) 1) "LUA"
           2) 1) "libraries_count"
              2) (integer) 3
              3) "functions_count"
              4) (integer) 5

        1) "running_script"
        2) (nil)
        3) "engines"
        4) ...

        RESP3 response example
        1# "running_script" =>
           1# "name" => "<function name>"
           2# "command" =>
              1) "fcall"
              2) "<function name>"
              ... rest `fcall` args ...
           3# "duration_ms" => (integer) 5000
        2# "engines" =>
           1# "LUA" =>
              1# "libraries_count" => (integer) 3
              2# "functions_count" => (integer) 5
        */
        // First part of the response (`running_script`) is converted as `Map[str, any]`
        // Second part is converted as `Map[str, Map[str, int]]`
        ExpectedReturnType::FunctionStatsReturnType => match value {
            // TODO reuse https://github.com/Bit-Quill/glide-for-redis/pull/331 and https://github.com/aws/glide-for-redis/pull/1489
            Value::Map(map) => {
                if map[0].0 == Value::BulkString(b"running_script".into()) {
                    // already a RESP3 response - do nothing
                    Ok(Value::Map(map))
                } else {
                    // cluster (multi-node) response - go recursive
                    convert_map_entries(
                        map,
                        Some(ExpectedReturnType::BulkString),
                        Some(ExpectedReturnType::FunctionStatsReturnType),
                    )
                }
            }
            Value::Array(mut array) if array.len() == 4 => {
                let mut result: Vec<(Value, Value)> = Vec::with_capacity(2);
                let running_script_info = array.remove(1);
                let running_script_converted = match running_script_info {
                    Value::Nil => Ok(Value::Nil),
                    Value::Array(inner_map_as_array) => {
                        convert_array_to_map_by_type(inner_map_as_array, None, None)
                    }
                    _ => Err((ErrorKind::TypeError, "Response couldn't be converted").into()),
                };
                result.push((array.remove(0), running_script_converted?));
                let Value::Array(engines_info) = array.remove(1) else {
                    return Err((ErrorKind::TypeError, "Incorrect value type received").into());
                };
                let engines_info_converted = convert_array_to_map_by_type(
                    engines_info,
                    Some(ExpectedReturnType::BulkString),
                    Some(ExpectedReturnType::Map {
                        key_type: &None,
                        value_type: &None,
                    }),
                );
                result.push((array.remove(0), engines_info_converted?));

                Ok(Value::Map(result))
            }
            _ => Err((ErrorKind::TypeError, "Response couldn't be converted").into()),
        },
        // Used by XAUTOCLAIM. The command returns a list of length 2 if the server version is less than 7.0.0 or a list
        // of length 3 otherwise. It has the following response format:
        /* server version < 7.0.0 example:
        1) "0-0"
        2) 1) 1) "1-0"
              2) 1) "field1"
                 2) "value1"
                 3) "field2"
                 4) "value2"
           2) 1) "1-1"
              2) 1) "field3"
                 2) "value3"
           3) (nil)  // Entry IDs that were in the Pending Entry List but no longer in the stream get a nil value.
           4) (nil)  // These nil values will be dropped so that we can return a map value for the second response element.

        server version >= 7.0.0 example:
        1) "0-0"
        2) 1) 1) "1-0"
              2) 1) "field1"
                 2) "value1"
                 3) "field2"
                 4) "value2"
           2) 1) "1-1"
              2) 1) "field3"
                 2) "value3"
        3) 1) "1-2"  // Entry IDs that were in the Pending Entry List but no longer in the stream are listed in the
           2) "1-3"  // third response element, which is an array of these IDs.
        */
        ExpectedReturnType::XAutoClaimReturnType => match value {
            // Response will have 2 elements if server version < 7.0.0, and 3 elements otherwise.
            Value::Array(mut array) if array.len() == 2 || array.len() == 3 => {
                let mut result: Vec<Value> = Vec::with_capacity(array.len());
                // The first element is always a stream ID as a string, so the clone is cheap.
                result.push(array[0].clone());

                let mut stale_entry_ids: Option<Value> = None;
                if array.len() == 3 {
                    // We use array.remove to avoid having to clone the other element(s). If we removed the second
                    // element before the third, the third would have to be shifted, so we remove the third element
                    // first to improve performance.
                    stale_entry_ids = Some(array.remove(2));
                }

                // Only the element at index 1 needs conversion.
                result.push(convert_to_expected_type(
                    array.remove(1),
                    Some(ExpectedReturnType::Map {
                        key_type: &Some(ExpectedReturnType::BulkString),
                        value_type: &Some(ExpectedReturnType::ArrayOfPairs),
                    })
                )?);

                if let Some(value) = stale_entry_ids {
                    result.push(value);
                }

                Ok(Value::Array(result))
            },
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to an XAUTOCLAIM response",
                format!("(response was {:?})", get_value_type(&value)),
            )
                .into()),
        },
    }
}

/// Similar to [`convert_array_to_map_by_type`], but converts keys and values to the given types inside the map.
/// The input data is [`Value::Map`] payload, the output is the new [`Value::Map`].
fn convert_map_entries(
    map: Vec<(Value, Value)>,
    key_type: Option<ExpectedReturnType>,
    value_type: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    let result = map
        .into_iter()
        .map(|(key, inner_value)| {
            let converted_key = convert_to_expected_type(key, key_type)?;
            let converted_value = convert_to_expected_type(inner_value, value_type)?;
            Ok((converted_key, converted_value))
        })
        .collect::<RedisResult<_>>();

    result.map(Value::Map)
}

/// Convert string returned by `LOLWUT` command.
/// The input string is shell-friendly and contains color codes and escape sequences.
/// The output string is user-friendly, colored whitespaces replaced with corresponding symbols.
fn convert_lolwut_string(data: &str) -> String {
    if data.contains("\x1b[0m") {
        data.replace("\x1b[0;97;107m \x1b[0m", "\u{2591}")
            .replace("\x1b[0;37;47m \x1b[0m", "\u{2592}")
            .replace("\x1b[0;90;100m \x1b[0m", "\u{2593}")
            .replace("\x1b[0;30;40m \x1b[0m", " ")
    } else {
        data.to_owned()
    }
}

/// Converts elements in an array to the specified type.
///
/// `array` is an array of values.
/// `element_type` is the type that the array elements should be converted to.
fn convert_array_elements(
    array: Vec<Value>,
    element_type: ExpectedReturnType,
) -> RedisResult<Value> {
    let converted_array = array
        .iter()
        .map(|v| convert_to_expected_type(v.clone(), Some(element_type)).unwrap())
        .collect();
    Ok(Value::Array(converted_array))
}

/// Converts an array of flat maps into an array of maps.
/// Input:
/// ```text
/// 1) 1) "map 1 key 1"
///    2) "map 1 value 1"
///    3) "map 1 key 2"
///    4) "map 1 value 2"
///    ...
/// 2) 1) "map 2 key 1"
///    2) "map 2 value 1"
///    ...
/// ```
/// Output:
/// ```text
///  1) 1# "map 1 key 1" => "map 1 value 1"
///     2# "map 1 key 2" => "map 1 value 2"
///     ...
///  2) 1# "map 2 key 1" => "map 2 value 1"
///     ...
/// ```
///
/// `array` is an array of arrays, where each inner array represents data for a map. The inner arrays contain map keys at even-positioned elements and map values at odd-positioned elements.
/// `value_expected_return_type` is the desired type for the map values.
fn convert_array_of_flat_maps(
    array: Vec<Value>,
    value_expected_return_type: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    let mut result: Vec<Value> = Vec::with_capacity(array.len());
    for entry in array {
        let Value::Array(entry_as_array) = entry else {
            return Err((ErrorKind::TypeError, "Incorrect value type received").into());
        };
        let map = convert_array_to_map_by_type(
            entry_as_array,
            Some(ExpectedReturnType::BulkString),
            value_expected_return_type,
        )?;
        result.push(map);
    }
    Ok(Value::Array(result))
}

/// Converts key-value elements in a given map using the specified types.
///
/// `map` A vector of key-values.
/// `key_type` is used to convert each key when collecting into the resulting map.
/// If `None` is given, then the key is not converted.
/// `value_type` is used to convert each value when collecting into the resulting map.
/// If `None` is given, then the value is not converted.
fn convert_inner_map_by_type(
    map: Vec<(Value, Value)>,
    key_type: Option<ExpectedReturnType>,
    value_type: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    let result = map
        .into_iter()
        .map(|(key, inner_value)| {
            Ok((
                convert_to_expected_type(key, key_type)?,
                convert_to_expected_type(inner_value, value_type)?,
            ))
        })
        .collect::<RedisResult<_>>();

    result.map(Value::Map)
}

/// Converts the given array into a map, and converts key-value elements using the specified types.
///
/// `array` Aa 2-dimensional array. Each entry of the array has two values: the first
/// element is the key for the map, and the second element is the value for the map.
/// `key_type` is used to convert each key when collecting into the resulting map.
/// If `None` is given, then the key is not converted.
/// `value_type` is used to convert each value when collecting into the resulting map.
/// If `None` is given, then the value is not converted.
fn convert_array_to_map_by_type(
    array: Vec<Value>,
    key_type: Option<ExpectedReturnType>,
    value_type: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    let mut map = Vec::new();
    let mut iterator = array.into_iter();
    while let Some(key) = iterator.next() {
        match key {
            Value::Array(inner_array) => {
                if inner_array.len() != 2 {
                    return Err((
                        ErrorKind::TypeError,
                        "Array inside map must contain exactly two elements",
                    )
                        .into());
                }
                let mut inner_iterator = inner_array.into_iter();
                let Some(inner_key) = inner_iterator.next() else {
                    return Err((ErrorKind::TypeError, "Missing key inside array of map").into());
                };
                let Some(inner_value) = inner_iterator.next() else {
                    return Err((ErrorKind::TypeError, "Missing value inside array of map").into());
                };
                map.push((
                    convert_to_expected_type(inner_key, key_type)?,
                    convert_to_expected_type(inner_value, value_type)?,
                ));
            }
            Value::Nil => {
                // Ignore nil key values - they will not be placed in the map. This is necessary for commands like
                // XAUTOCLAIM, which can contain an array representation of a map with nil keys in place of stream IDs
                // that existed in the Pending Entries List but no longer existed in the stream.
                continue;
            }
            _ => {
                let Some(value) = iterator.next() else {
                    return Err((
                        ErrorKind::TypeError,
                        "Response has odd number of items, and cannot be entered into a map",
                    )
                        .into());
                };
                map.push((
                    convert_to_expected_type(key, key_type)?,
                    convert_to_expected_type(value, value_type)?,
                ));
            }
        }
    }
    Ok(Value::Map(map))
}

/// Used by commands like ZRANDMEMBER and HRANDFIELD. Normally a map would be more suitable for these key-value responses, but these commands may return duplicate key-value pairs depending on the command arguments. These duplicated pairs cannot be represented by a map.
///
/// Converts a server response as follows:
/// - if the server returned an empty array, return an empty array.
/// - if the server returned a flat array (RESP2), convert it to a two-dimensional array, where the inner arrays are length=2 arrays representing key-value pairs.
/// - if the server returned a two-dimensional array (RESP3), return the response as is, since it is already in the correct format.
/// - otherwise, return an error.
///
/// `response` is a server response that we should attempt to convert as described above.
/// `value_expected_return_type` indicates the desired return type of the values in the key-value pairs. The values will only be converted if the response was a flat array, since RESP3 already returns an array of pairs with values already of the correct type.
fn convert_to_array_of_pairs(
    response: Value,
    value_expected_return_type: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    match response {
        Value::Nil => Ok(response),
        Value::Array(ref array) if array.is_empty() || matches!(array[0], Value::Array(_)) => {
            // The server response is an empty array or a RESP3 array of pairs. In RESP3, the values in the pairs are
            // already of the correct type, so we do not need to convert them and `response` is in the correct format.
            Ok(response)
        }
        Value::Array(array)
            if array.len() % 2 == 0
                && matches!(array[0], Value::BulkString(_) | Value::SimpleString(_)) =>
        {
            // The server response is a RESP2 flat array with keys at even indices and their associated values at
            // odd indices.
            convert_flat_array_to_array_of_pairs(array, value_expected_return_type)
        }
        _ => Err((
            ErrorKind::TypeError,
            "Response couldn't be converted to an array of key-value pairs",
            format!("(response was {:?})", get_value_type(&response)),
        )
            .into()),
    }
}

/// Converts a flat array of values to a two-dimensional array, where the inner arrays are length=2 arrays representing key-value pairs. Normally a map would be more suitable for these responses, but some commands (eg HRANDFIELD) may return duplicate key-value pairs depending on the command arguments. These duplicated pairs cannot be represented by a map.
///
/// `array` is a flat array containing keys at even-positioned elements and their associated values at odd-positioned elements.
/// `value_expected_return_type` indicates the desired return type of the values in the key-value pairs.
fn convert_flat_array_to_array_of_pairs(
    array: Vec<Value>,
    value_expected_return_type: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    if array.len() % 2 != 0 {
        return Err((
            ErrorKind::TypeError,
            "Response has odd number of items, and cannot be converted to an array of key-value pairs"
        )
            .into());
    }

    let mut result = Vec::with_capacity(array.len() / 2);
    for i in (0..array.len()).step_by(2) {
        let key = array[i].clone();
        let value = convert_to_expected_type(array[i + 1].clone(), value_expected_return_type)?;
        let pair = vec![key, value];
        result.push(Value::Array(pair));
    }
    Ok(Value::Array(result))
}

pub(crate) fn expected_type_for_cmd(cmd: &Cmd) -> Option<ExpectedReturnType> {
    let command = cmd.command()?;

    // TODO use enum to avoid mistakes
    match command.as_slice() {
        b"HGETALL" | b"CONFIG GET" | b"FT.CONFIG GET" | b"HELLO" => Some(ExpectedReturnType::Map {
            key_type: &None,
            value_type: &None,
        }),
        b"XCLAIM" => {
            if cmd.position(b"JUSTID").is_some() {
                Some(ExpectedReturnType::ArrayOfStrings)
            } else {
                Some(ExpectedReturnType::Map {
                    key_type: &Some(ExpectedReturnType::SimpleString),
                    value_type: &Some(ExpectedReturnType::ArrayOfPairs),
                })
            }
        }
        b"XAUTOCLAIM" => {
            if cmd.position(b"JUSTID").is_some() {
                // Value conversion is not needed if the JUSTID arg was passed.
                None
            } else {
                Some(ExpectedReturnType::XAutoClaimReturnType)
            }
        }
        b"XRANGE" | b"XREVRANGE" => Some(ExpectedReturnType::Map {
            key_type: &Some(ExpectedReturnType::BulkString),
            value_type: &Some(ExpectedReturnType::ArrayOfPairs),
        }),
        b"XREAD" | b"XREADGROUP" => Some(ExpectedReturnType::Map {
            key_type: &Some(ExpectedReturnType::BulkString),
            value_type: &Some(ExpectedReturnType::Map {
                key_type: &Some(ExpectedReturnType::BulkString),
                value_type: &Some(ExpectedReturnType::ArrayOfPairs),
            }),
        }),
        b"LCS" => cmd.position(b"IDX").map(|_| ExpectedReturnType::Map {
            key_type: &Some(ExpectedReturnType::SimpleString),
            value_type: &None,
        }),
        b"INCRBYFLOAT" | b"HINCRBYFLOAT" | b"ZINCRBY" => Some(ExpectedReturnType::Double),
        b"HEXISTS"
        | b"HSETNX"
        | b"EXPIRE"
        | b"EXPIREAT"
        | b"PEXPIRE"
        | b"PEXPIREAT"
        | b"SISMEMBER"
        | b"PERSIST"
        | b"SMOVE"
        | b"RENAMENX"
        | b"MOVE"
        | b"COPY"
        | b"MSETNX"
        | b"XGROUP DESTROY"
        | b"XGROUP CREATECONSUMER" => Some(ExpectedReturnType::Boolean),
        b"SMISMEMBER" => Some(ExpectedReturnType::ArrayOfBools),
        b"SMEMBERS" | b"SINTER" | b"SDIFF" | b"SUNION" => Some(ExpectedReturnType::Set),
        b"ZSCORE" | b"GEODIST" => Some(ExpectedReturnType::DoubleOrNull),
        b"ZMSCORE" => Some(ExpectedReturnType::ArrayOfDoubleOrNull),
        b"ZPOPMIN" | b"ZPOPMAX" => Some(ExpectedReturnType::MapOfStringToDouble),
        b"BZMPOP" | b"ZMPOP" => Some(ExpectedReturnType::ZMPopReturnType),
        b"JSON.TOGGLE" => Some(ExpectedReturnType::JsonToggleReturnType),
        b"GEOPOS" => Some(ExpectedReturnType::ArrayOfArraysOfDoubleOrNull),
        b"LMPOP" => Some(ExpectedReturnType::ArrayOfStringAndArrays),
        b"BLMPOP" => Some(ExpectedReturnType::ArrayOfStringAndArrays),
        b"HRANDFIELD" => cmd
            .position(b"WITHVALUES")
            .map(|_| ExpectedReturnType::ArrayOfPairs),
        b"ZRANDMEMBER" => cmd
            .position(b"WITHSCORES")
            .map(|_| ExpectedReturnType::ArrayOfMemberScorePairs),
        b"ZADD" => cmd
            .position(b"INCR")
            .map(|_| ExpectedReturnType::DoubleOrNull),
        b"ZRANGE" | b"ZDIFF" | b"ZUNION" | b"ZINTER" => cmd
            .position(b"WITHSCORES")
            .map(|_| ExpectedReturnType::MapOfStringToDouble),
        b"ZRANK" | b"ZREVRANK" => cmd
            .position(b"WITHSCORE")
            .map(|_| ExpectedReturnType::ZRankReturnType),
        b"BZPOPMIN" | b"BZPOPMAX" => Some(ExpectedReturnType::KeyWithMemberAndScore),
        b"SPOP" => {
            if cmd.arg_idx(2).is_some() {
                Some(ExpectedReturnType::Set)
            } else {
                None
            }
        }
        b"LOLWUT" => Some(ExpectedReturnType::Lolwut),
        b"FUNCTION LIST" => Some(ExpectedReturnType::ArrayOfMaps(
            &ExpectedReturnType::ArrayOfMaps(&ExpectedReturnType::StringOrSet),
        )),
        b"FUNCTION STATS" => Some(ExpectedReturnType::FunctionStatsReturnType),
        b"GEOSEARCH" => {
            if cmd.position(b"WITHDIST").is_some()
                || cmd.position(b"WITHHASH").is_some()
                || cmd.position(b"WITHCOORD").is_some()
            {
                Some(ExpectedReturnType::GeoSearchReturnType)
            } else {
                None
            }
        }
        _ => None,
    }
}

/// Gets the enum variant as a string for the `value` given.
pub(crate) fn get_value_type<'a>(value: &Value) -> &'a str {
    match value {
        Value::Nil => "Nil",
        Value::Int(_) => "Int",
        Value::BulkString(_) => "BulkString",
        Value::Array(_) => "Array",
        Value::SimpleString(_) => "SimpleString",
        Value::Okay => "OK",
        Value::Map(_) => "Map",
        Value::Attribute { .. } => "Attribute",
        Value::Set(_) => "Set",
        Value::Double(_) => "Double",
        Value::Boolean(_) => "Boolean",
        Value::VerbatimString { .. } => "VerbatimString",
        Value::BigNumber(_) => "BigNumber",
        Value::Push { .. } => "Push",
        // TODO Value::ServerError from https://github.com/redis-rs/redis-rs/pull/1093
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn convert_function_list() {
        let command = &mut redis::cmd("FUNCTION");
        command.arg("LIST");
        let expected_type = expected_type_for_cmd(command);

        assert!(matches!(
            expected_type,
            Some(ExpectedReturnType::ArrayOfMaps(_))
        ));

        let resp2_response = Value::Array(vec![
            Value::Array(vec![
                Value::BulkString("library_name".to_string().into_bytes()),
                Value::BulkString("mylib1".to_string().into_bytes()),
                Value::BulkString("engine".to_string().into_bytes()),
                Value::BulkString("LUA".to_string().into_bytes()),
                Value::BulkString("functions".to_string().into_bytes()),
                Value::Array(vec![
                    Value::Array(vec![
                        Value::BulkString("name".to_string().into_bytes()),
                        Value::BulkString("myfunc1".to_string().into_bytes()),
                        Value::BulkString("description".to_string().into_bytes()),
                        Value::Nil,
                        Value::BulkString("flags".to_string().into_bytes()),
                        Value::Array(vec![
                            Value::BulkString("read".to_string().into_bytes()),
                            Value::BulkString("write".to_string().into_bytes()),
                        ]),
                    ]),
                    Value::Array(vec![
                        Value::BulkString("name".to_string().into_bytes()),
                        Value::BulkString("myfunc2".to_string().into_bytes()),
                        Value::BulkString("description".to_string().into_bytes()),
                        Value::BulkString("blahblah".to_string().into_bytes()),
                        Value::BulkString("flags".to_string().into_bytes()),
                        Value::Array(vec![]),
                    ]),
                ]),
            ]),
            Value::Array(vec![
                Value::BulkString("library_name".to_string().into_bytes()),
                Value::BulkString("mylib2".to_string().into_bytes()),
                Value::BulkString("engine".to_string().into_bytes()),
                Value::BulkString("LUA".to_string().into_bytes()),
                Value::BulkString("functions".to_string().into_bytes()),
                Value::Array(vec![]),
                Value::BulkString("library_code".to_string().into_bytes()),
                Value::BulkString("<code>".to_string().into_bytes()),
            ]),
        ]);

        let resp3_response = Value::Array(vec![
            Value::Map(vec![
                (
                    Value::BulkString("library_name".to_string().into_bytes()),
                    Value::BulkString("mylib1".to_string().into_bytes()),
                ),
                (
                    Value::BulkString("engine".to_string().into_bytes()),
                    Value::BulkString("LUA".to_string().into_bytes()),
                ),
                (
                    Value::BulkString("functions".to_string().into_bytes()),
                    Value::Array(vec![
                        Value::Map(vec![
                            (
                                Value::BulkString("name".to_string().into_bytes()),
                                Value::BulkString("myfunc1".to_string().into_bytes()),
                            ),
                            (
                                Value::BulkString("description".to_string().into_bytes()),
                                Value::Nil,
                            ),
                            (
                                Value::BulkString("flags".to_string().into_bytes()),
                                Value::Set(vec![
                                    Value::BulkString("read".to_string().into_bytes()),
                                    Value::BulkString("write".to_string().into_bytes()),
                                ]),
                            ),
                        ]),
                        Value::Map(vec![
                            (
                                Value::BulkString("name".to_string().into_bytes()),
                                Value::BulkString("myfunc2".to_string().into_bytes()),
                            ),
                            (
                                Value::BulkString("description".to_string().into_bytes()),
                                Value::BulkString("blahblah".to_string().into_bytes()),
                            ),
                            (
                                Value::BulkString("flags".to_string().into_bytes()),
                                Value::Set(vec![]),
                            ),
                        ]),
                    ]),
                ),
            ]),
            Value::Map(vec![
                (
                    Value::BulkString("library_name".to_string().into_bytes()),
                    Value::BulkString("mylib2".to_string().into_bytes()),
                ),
                (
                    Value::BulkString("engine".to_string().into_bytes()),
                    Value::BulkString("LUA".to_string().into_bytes()),
                ),
                (
                    Value::BulkString("functions".to_string().into_bytes()),
                    Value::Array(vec![]),
                ),
                (
                    Value::BulkString("library_code".to_string().into_bytes()),
                    Value::BulkString("<code>".to_string().into_bytes()),
                ),
            ]),
        ]);

        let resp2_cluster_response = Value::Map(vec![
            (Value::BulkString("node1".into()), resp2_response.clone()),
            (Value::BulkString("node2".into()), resp2_response.clone()),
            (Value::BulkString("node3".into()), resp2_response.clone()),
        ]);

        let resp3_cluster_response = Value::Map(vec![
            (Value::BulkString("node1".into()), resp3_response.clone()),
            (Value::BulkString("node2".into()), resp3_response.clone()),
            (Value::BulkString("node3".into()), resp3_response.clone()),
        ]);

        // convert RESP2 -> RESP3
        assert_eq!(
            convert_to_expected_type(resp2_response.clone(), expected_type).unwrap(),
            resp3_response.clone()
        );

        // convert RESP3 -> RESP3
        assert_eq!(
            convert_to_expected_type(resp3_response.clone(), expected_type).unwrap(),
            resp3_response.clone()
        );

        // convert cluster RESP2 -> RESP3
        assert_eq!(
            convert_to_expected_type(resp2_cluster_response.clone(), expected_type).unwrap(),
            resp3_cluster_response.clone()
        );

        // convert cluster RESP3 -> RESP3
        assert_eq!(
            convert_to_expected_type(resp3_cluster_response.clone(), expected_type).unwrap(),
            resp3_cluster_response.clone()
        );
    }

    #[test]
    fn convert_lolwut() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("LOLWUT").arg("version").arg("42")),
            Some(ExpectedReturnType::Lolwut)
        ));

        let unconverted_string : String = "\x1b[0;97;107m \x1b[0m--\x1b[0;37;47m \x1b[0m--\x1b[0;90;100m \x1b[0m--\x1b[0;30;40m \x1b[0m".into();
        let expected: String = "\u{2591}--\u{2592}--\u{2593}-- ".into();

        let converted_1 = convert_to_expected_type(
            Value::BulkString(unconverted_string.clone().into_bytes()),
            Some(ExpectedReturnType::Lolwut),
        );
        assert_eq!(
            Value::BulkString(expected.clone().into_bytes()),
            converted_1.unwrap()
        );

        let converted_2 = convert_to_expected_type(
            Value::VerbatimString {
                format: redis::VerbatimFormat::Text,
                text: unconverted_string.clone(),
            },
            Some(ExpectedReturnType::Lolwut),
        );
        assert_eq!(
            Value::BulkString(expected.clone().into_bytes()),
            converted_2.unwrap()
        );

        let converted_3 = convert_to_expected_type(
            Value::Map(vec![
                (
                    Value::SimpleString("node 1".into()),
                    Value::BulkString(unconverted_string.clone().into_bytes()),
                ),
                (
                    Value::SimpleString("node 2".into()),
                    Value::BulkString(unconverted_string.clone().into_bytes()),
                ),
            ]),
            Some(ExpectedReturnType::Lolwut),
        );
        assert_eq!(
            Value::Map(vec![
                (
                    Value::BulkString("node 1".into()),
                    Value::BulkString(expected.clone().into_bytes())
                ),
                (
                    Value::BulkString("node 2".into()),
                    Value::BulkString(expected.clone().into_bytes())
                ),
            ]),
            converted_3.unwrap()
        );

        let converted_4 = convert_to_expected_type(
            Value::SimpleString(unconverted_string.clone()),
            Some(ExpectedReturnType::Lolwut),
        );
        assert!(converted_4.is_err());
    }

    #[test]
    fn convert_xclaim() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("XCLAIM")
                    .arg("key")
                    .arg("grou")
                    .arg("consumer")
                    .arg("0")
                    .arg("id")
            ),
            Some(ExpectedReturnType::Map {
                key_type: &Some(ExpectedReturnType::SimpleString),
                value_type: &Some(ExpectedReturnType::ArrayOfPairs),
            })
        ));
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("XCLAIM")
                    .arg("key")
                    .arg("grou")
                    .arg("consumer")
                    .arg("0")
                    .arg("id")
                    .arg("JUSTID")
            ),
            Some(ExpectedReturnType::ArrayOfStrings)
        ));
    }

    #[test]
    fn convert_xrange_xrevrange() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("XRANGE").arg("key").arg("start").arg("end")),
            Some(ExpectedReturnType::Map {
                key_type: &Some(ExpectedReturnType::BulkString),
                value_type: &Some(ExpectedReturnType::ArrayOfPairs),
            })
        ));
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("XREVRANGE").arg("key").arg("end").arg("start")),
            Some(ExpectedReturnType::Map {
                key_type: &Some(ExpectedReturnType::BulkString),
                value_type: &Some(ExpectedReturnType::ArrayOfPairs),
            })
        ));
    }

    #[test]
    fn convert_xread() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("XREAD").arg("streams").arg("key").arg("id")),
            Some(ExpectedReturnType::Map {
                key_type: &Some(ExpectedReturnType::BulkString),
                value_type: &Some(ExpectedReturnType::Map {
                    key_type: &Some(ExpectedReturnType::BulkString),
                    value_type: &Some(ExpectedReturnType::ArrayOfPairs),
                }),
            })
        ));
    }

    #[test]
    fn convert_xreadgroup() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("XREADGROUP")
                    .arg("GROUP")
                    .arg("group")
                    .arg("consumer")
                    .arg("streams")
                    .arg("key")
                    .arg("id")
            ),
            Some(ExpectedReturnType::Map {
                key_type: &Some(ExpectedReturnType::BulkString),
                value_type: &Some(ExpectedReturnType::Map {
                    key_type: &Some(ExpectedReturnType::BulkString),
                    value_type: &Some(ExpectedReturnType::ArrayOfPairs),
                }),
            })
        ));
    }

    #[test]
    fn convert_xautoclaim() {
        // Value conversion is not needed if the JUSTID arg was passed.
        assert!(expected_type_for_cmd(
            redis::cmd("XAUTOCLAIM")
                .arg("key")
                .arg("group")
                .arg("consumer")
                .arg("0")
                .arg("0-0")
                .arg("JUSTID")
        )
        .is_none());

        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("XAUTOCLAIM")
                    .arg("key")
                    .arg("group")
                    .arg("consumer")
                    .arg("0")
                    .arg("0-0")
            ),
            Some(ExpectedReturnType::XAutoClaimReturnType)
        ));

        let v6_response = Value::Array(vec![
            Value::BulkString("0-0".to_string().into_bytes()),
            Value::Array(vec![
                Value::Array(vec![
                    Value::BulkString("1-0".to_string().into_bytes()),
                    Value::Array(vec![
                        Value::BulkString("field1".to_string().into_bytes()),
                        Value::BulkString("value1".to_string().into_bytes()),
                        Value::BulkString("field2".to_string().into_bytes()),
                        Value::BulkString("value2".to_string().into_bytes()),
                    ]),
                ]),
                Value::Nil, // Entry IDs that were in the Pending Entry List but no longer in the stream get a nil value.
                Value::Array(vec![
                    Value::BulkString("1-1".to_string().into_bytes()),
                    Value::Array(vec![
                        Value::BulkString("field3".to_string().into_bytes()),
                        Value::BulkString("value3".to_string().into_bytes()),
                    ]),
                ]),
            ]),
        ]);

        let expected_v6_response = Value::Array(vec![
            Value::BulkString("0-0".to_string().into_bytes()),
            Value::Map(vec![
                (
                    Value::BulkString("1-0".to_string().into_bytes()),
                    Value::Array(vec![
                        Value::Array(vec![
                            Value::BulkString("field1".to_string().into_bytes()),
                            Value::BulkString("value1".to_string().into_bytes()),
                        ]),
                        Value::Array(vec![
                            Value::BulkString("field2".to_string().into_bytes()),
                            Value::BulkString("value2".to_string().into_bytes()),
                        ]),
                    ]),
                ),
                (
                    Value::BulkString("1-1".to_string().into_bytes()),
                    Value::Array(vec![Value::Array(vec![
                        Value::BulkString("field3".to_string().into_bytes()),
                        Value::BulkString("value3".to_string().into_bytes()),
                    ])]),
                ),
            ]),
        ]);

        assert_eq!(
            convert_to_expected_type(
                v6_response.clone(),
                Some(ExpectedReturnType::XAutoClaimReturnType)
            )
            .unwrap(),
            expected_v6_response.clone()
        );

        let v7_response = Value::Array(vec![
            Value::BulkString("0-0".to_string().into_bytes()),
            Value::Array(vec![
                Value::Array(vec![
                    Value::BulkString("1-0".to_string().into_bytes()),
                    Value::Array(vec![
                        Value::BulkString("field1".to_string().into_bytes()),
                        Value::BulkString("value1".to_string().into_bytes()),
                        Value::BulkString("field2".to_string().into_bytes()),
                        Value::BulkString("value2".to_string().into_bytes()),
                    ]),
                ]),
                Value::Array(vec![
                    Value::BulkString("1-1".to_string().into_bytes()),
                    Value::Array(vec![
                        Value::BulkString("field3".to_string().into_bytes()),
                        Value::BulkString("value3".to_string().into_bytes()),
                    ]),
                ]),
            ]),
            Value::Array(vec![Value::BulkString("1-2".to_string().into_bytes())]),
        ]);

        let expected_v7_response = Value::Array(vec![
            Value::BulkString("0-0".to_string().into_bytes()),
            Value::Map(vec![
                (
                    Value::BulkString("1-0".to_string().into_bytes()),
                    Value::Array(vec![
                        Value::Array(vec![
                            Value::BulkString("field1".to_string().into_bytes()),
                            Value::BulkString("value1".to_string().into_bytes()),
                        ]),
                        Value::Array(vec![
                            Value::BulkString("field2".to_string().into_bytes()),
                            Value::BulkString("value2".to_string().into_bytes()),
                        ]),
                    ]),
                ),
                (
                    Value::BulkString("1-1".to_string().into_bytes()),
                    Value::Array(vec![Value::Array(vec![
                        Value::BulkString("field3".to_string().into_bytes()),
                        Value::BulkString("value3".to_string().into_bytes()),
                    ])]),
                ),
            ]),
            Value::Array(vec![Value::BulkString("1-2".to_string().into_bytes())]),
        ]);

        assert_eq!(
            convert_to_expected_type(
                v7_response.clone(),
                Some(ExpectedReturnType::XAutoClaimReturnType)
            )
            .unwrap(),
            expected_v7_response.clone()
        );
    }

    #[test]
    fn test_convert_empty_array_to_map_is_nil() {
        let mut cmd = redis::cmd("XREAD");
        let expected_type = expected_type_for_cmd(cmd.arg("STREAMS").arg("key").arg("id"));

        // test convert nil is OK
        assert_eq!(
            convert_to_expected_type(Value::Nil, expected_type,),
            Ok(Value::Nil)
        );
    }

    #[test]
    fn test_convert_array_to_map_with_none() {
        let unconverted_map = vec![
            (
                Value::BulkString(b"key1".to_vec()),
                Value::BulkString(b"10.5".to_vec()),
            ),
            (Value::Double(20.5), Value::Double(19.5)),
            (Value::Double(18.5), Value::BulkString(b"30.2".to_vec())),
        ];

        let converted_type = ExpectedReturnType::Map {
            key_type: &None,
            value_type: &None,
        };
        let converted_map =
            convert_to_expected_type(Value::Map(unconverted_map), Some(converted_type)).unwrap();

        let converted_map = if let Value::Map(map) = converted_map {
            map
        } else {
            panic!("Expected a Map, but got {:?}", converted_map);
        };

        assert_eq!(converted_map.len(), 3);

        let (key, value) = &converted_map[0];
        assert_eq!(*key, Value::BulkString(b"key1".to_vec()));
        assert_eq!(*value, Value::BulkString(b"10.5".to_vec()));

        let (key, value) = &converted_map[1];
        assert_eq!(*key, Value::Double(20.5));
        assert_eq!(*value, Value::Double(19.5));

        let (key, value) = &converted_map[2];
        assert_eq!(*key, Value::Double(18.5));
        assert_eq!(*value, Value::BulkString(b"30.2".to_vec()));
    }

    #[test]
    fn test_convert_2d_array_to_map_using_expected_return_type_map() {
        // in RESP2, we get an array of arrays value like this:
        // 1) 1) "key1"
        //    2) 1) 1) "streamid-1"
        //          2) 1) "f1"
        //             2) "v1"
        //             3) "f2"
        //             4) "v2"
        //       2) 1) "streamid-2" ...
        // 2) 1) "key2"...
        //
        let array_of_arrays = vec![
            Value::Array(vec![
                Value::BulkString(b"key1".to_vec()),
                Value::Array(vec![Value::Array(vec![
                    Value::BulkString(b"streamid-1".to_vec()),
                    Value::Array(vec![
                        Value::BulkString(b"field1".to_vec()),
                        Value::BulkString(b"value1".to_vec()),
                    ]),
                ])]),
            ]),
            Value::Array(vec![
                Value::BulkString(b"key2".to_vec()),
                Value::Array(vec![
                    Value::Array(vec![
                        Value::BulkString(b"streamid-2".to_vec()),
                        Value::Array(vec![
                            Value::BulkString(b"field21".to_vec()),
                            Value::BulkString(b"value21".to_vec()),
                            Value::BulkString(b"field22".to_vec()),
                            Value::BulkString(b"value22".to_vec()),
                        ]),
                    ]),
                    Value::Array(vec![
                        Value::BulkString(b"streamid-3".to_vec()),
                        Value::Array(vec![
                            Value::BulkString(b"field3".to_vec()),
                            Value::BulkString(b"value3".to_vec()),
                        ]),
                    ]),
                ]),
            ]),
        ];

        // convert to a map value like this:
        // #1) "key1"
        //    #1) "streamid-1"
        //         1) "f1"
        //         2) "v1"
        //         3) "f2"
        //         4) "v2"
        //    #2) "streamid-2"
        //    ...
        // #2) "key2"
        // ...
        let mut cmd = redis::cmd("XREAD");
        let expected_type = expected_type_for_cmd(cmd.arg("STREAMS").arg("key").arg("id"));
        let converted_map =
            convert_to_expected_type(Value::Array(array_of_arrays), expected_type).unwrap();

        let converted_map = if let Value::Map(map) = converted_map {
            map
        } else {
            panic!("Expected a Map, but got {:?}", converted_map);
        };
        // expect 2 keys
        assert_eq!(converted_map.len(), 2);

        let (key, value) = &converted_map[0];
        assert_eq!(Value::BulkString(b"key1".to_vec()), *key);
        assert_eq!(
            Value::Map(vec![(
                Value::BulkString(b"streamid-1".to_vec()),
                Value::Array(vec![Value::Array(vec![
                    Value::BulkString(b"field1".to_vec()),
                    Value::BulkString(b"value1".to_vec()),
                ]),]),
            ),]),
            *value,
        );

        let (key, value) = &converted_map[1];
        assert_eq!(*key, Value::BulkString(b"key2".to_vec()));
        assert_eq!(
            Value::Map(vec![
                (
                    Value::BulkString(b"streamid-2".to_vec()),
                    Value::Array(vec![
                        Value::Array(vec![
                            Value::BulkString(b"field21".to_vec()),
                            Value::BulkString(b"value21".to_vec()),
                        ]),
                        Value::Array(vec![
                            Value::BulkString(b"field22".to_vec()),
                            Value::BulkString(b"value22".to_vec()),
                        ]),
                    ]),
                ),
                (
                    Value::BulkString(b"streamid-3".to_vec()),
                    Value::Array(vec![Value::Array(vec![
                        Value::BulkString(b"field3".to_vec()),
                        Value::BulkString(b"value3".to_vec()),
                    ]),]),
                ),
            ]),
            *value,
        );
    }

    #[test]
    fn test_convert_map_with_inner_array_to_map_of_maps_using_expected_return_type_map() {
        // in RESP3, we get a map of arrays value like this:
        // 1# "key1" =>
        //    1) 1) "streamid-1"
        //       2) 1) "f1"
        //          2) "v1"
        //          3) "f2"
        //          4) "v2"
        //    2) 1) "streamid-2" ...
        // 2# "key2" => ...
        //
        let map_of_arrays = vec![
            (
                Value::BulkString("key1".into()),
                Value::Array(vec![Value::Array(vec![
                    Value::BulkString(b"streamid-1".to_vec()),
                    Value::Array(vec![
                        Value::BulkString(b"field1".to_vec()),
                        Value::BulkString(b"value1".to_vec()),
                    ]),
                ])]),
            ),
            (
                Value::BulkString("key2".into()),
                Value::Array(vec![
                    Value::Array(vec![
                        Value::BulkString(b"streamid-2".to_vec()),
                        Value::Array(vec![
                            Value::BulkString(b"field21".to_vec()),
                            Value::BulkString(b"value21".to_vec()),
                            Value::BulkString(b"field22".to_vec()),
                            Value::BulkString(b"value22".to_vec()),
                        ]),
                    ]),
                    Value::Array(vec![
                        Value::BulkString(b"streamid-3".to_vec()),
                        Value::Array(vec![
                            Value::BulkString(b"field3".to_vec()),
                            Value::BulkString(b"value3".to_vec()),
                        ]),
                    ]),
                ]),
            ),
        ];

        // convert to a map value like this:
        // #1) "key1"
        //    #1) "streamid-1"
        //         1) "f1"
        //         2) "v1"
        //         3) "f2"
        //         4) "v2"
        //    #2) "streamid-2"
        //    ...
        // #2) "key2"
        // ...
        let mut cmd = redis::cmd("XREAD");
        let expected_type = expected_type_for_cmd(cmd.arg("STREAMS").arg("key").arg("id"));
        let converted_map =
            convert_to_expected_type(Value::Map(map_of_arrays), expected_type).unwrap();

        let converted_map = if let Value::Map(map) = converted_map {
            map
        } else {
            panic!("Expected a Map, but got {:?}", converted_map);
        };

        assert_eq!(converted_map.len(), 2);

        let (key, value) = &converted_map[0];
        assert_eq!(Value::BulkString(b"key1".to_vec()), *key);
        assert_eq!(
            Value::Map(vec![(
                Value::BulkString(b"streamid-1".to_vec()),
                Value::Array(vec![Value::Array(vec![
                    Value::BulkString(b"field1".to_vec()),
                    Value::BulkString(b"value1".to_vec()),
                ]),]),
            ),]),
            *value,
        );

        let (key, value) = &converted_map[1];
        assert_eq!(*key, Value::BulkString(b"key2".to_vec()));
        assert_eq!(
            Value::Map(vec![
                (
                    Value::BulkString(b"streamid-2".to_vec()),
                    Value::Array(vec![
                        Value::Array(vec![
                            Value::BulkString(b"field21".to_vec()),
                            Value::BulkString(b"value21".to_vec()),
                        ]),
                        Value::Array(vec![
                            Value::BulkString(b"field22".to_vec()),
                            Value::BulkString(b"value22".to_vec()),
                        ]),
                    ]),
                ),
                (
                    Value::BulkString(b"streamid-3".to_vec()),
                    Value::Array(vec![Value::Array(vec![
                        Value::BulkString(b"field3".to_vec()),
                        Value::BulkString(b"value3".to_vec()),
                    ]),]),
                ),
            ]),
            *value,
        );
    }

    #[test]
    fn convert_function_stats() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("FUNCTION").arg("STATS")),
            Some(ExpectedReturnType::FunctionStatsReturnType)
        ));

        let resp2_response_non_empty_first_part_data = vec![
            Value::BulkString(b"running_script".into()),
            Value::Array(vec![
                Value::BulkString(b"name".into()),
                Value::BulkString(b"<function name>".into()),
                Value::BulkString(b"command".into()),
                Value::Array(vec![
                    Value::BulkString(b"fcall".into()),
                    Value::BulkString(b"<function name>".into()),
                    Value::BulkString(b"... rest `fcall` args ...".into()),
                ]),
                Value::BulkString(b"duration_ms".into()),
                Value::Int(24529),
            ]),
        ];

        let resp2_response_empty_first_part_data =
            vec![Value::BulkString(b"running_script".into()), Value::Nil];

        let resp2_response_second_part_data = vec![
            Value::BulkString(b"engines".into()),
            Value::Array(vec![
                Value::BulkString(b"LUA".into()),
                Value::Array(vec![
                    Value::BulkString(b"libraries_count".into()),
                    Value::Int(3),
                    Value::BulkString(b"functions_count".into()),
                    Value::Int(5),
                ]),
            ]),
        ];
        let resp2_response_with_non_empty_first_part = Value::Array(
            [
                resp2_response_non_empty_first_part_data.clone(),
                resp2_response_second_part_data.clone(),
            ]
            .concat(),
        );

        let resp2_response_with_empty_first_part = Value::Array(
            [
                resp2_response_empty_first_part_data.clone(),
                resp2_response_second_part_data.clone(),
            ]
            .concat(),
        );

        let resp2_cluster_response = Value::Map(vec![
            (
                Value::BulkString(b"node1".into()),
                resp2_response_with_non_empty_first_part.clone(),
            ),
            (
                Value::BulkString(b"node2".into()),
                resp2_response_with_empty_first_part.clone(),
            ),
            (
                Value::BulkString(b"node3".into()),
                resp2_response_with_empty_first_part.clone(),
            ),
        ]);

        let resp3_response_non_empty_first_part_data = vec![(
            Value::BulkString(b"running_script".into()),
            Value::Map(vec![
                (
                    Value::BulkString(b"name".into()),
                    Value::BulkString(b"<function name>".into()),
                ),
                (
                    Value::BulkString(b"command".into()),
                    Value::Array(vec![
                        Value::BulkString(b"fcall".into()),
                        Value::BulkString(b"<function name>".into()),
                        Value::BulkString(b"... rest `fcall` args ...".into()),
                    ]),
                ),
                (Value::BulkString(b"duration_ms".into()), Value::Int(24529)),
            ]),
        )];

        let resp3_response_empty_first_part_data =
            vec![(Value::BulkString(b"running_script".into()), Value::Nil)];

        let resp3_response_second_part_data = vec![(
            Value::BulkString(b"engines".into()),
            Value::Map(vec![(
                Value::BulkString(b"LUA".into()),
                Value::Map(vec![
                    (Value::BulkString(b"libraries_count".into()), Value::Int(3)),
                    (Value::BulkString(b"functions_count".into()), Value::Int(5)),
                ]),
            )]),
        )];

        let resp3_response_with_non_empty_first_part = Value::Map(
            [
                resp3_response_non_empty_first_part_data.clone(),
                resp3_response_second_part_data.clone(),
            ]
            .concat(),
        );

        let resp3_response_with_empty_first_part = Value::Map(
            [
                resp3_response_empty_first_part_data.clone(),
                resp3_response_second_part_data.clone(),
            ]
            .concat(),
        );

        let resp3_cluster_response = Value::Map(vec![
            (
                Value::BulkString(b"node1".into()),
                resp3_response_with_non_empty_first_part.clone(),
            ),
            (
                Value::BulkString(b"node2".into()),
                resp3_response_with_empty_first_part.clone(),
            ),
            (
                Value::BulkString(b"node3".into()),
                resp3_response_with_empty_first_part.clone(),
            ),
        ]);

        let conversion_type = Some(ExpectedReturnType::FunctionStatsReturnType);
        // resp2 -> resp3 conversion with non-empty `running_script` block
        assert_eq!(
            convert_to_expected_type(
                resp2_response_with_non_empty_first_part.clone(),
                conversion_type
            ),
            Ok(resp3_response_with_non_empty_first_part.clone())
        );
        // resp2 -> resp3 conversion with empty `running_script` block
        assert_eq!(
            convert_to_expected_type(
                resp2_response_with_empty_first_part.clone(),
                conversion_type
            ),
            Ok(resp3_response_with_empty_first_part.clone())
        );
        // resp2 -> resp3 cluster response
        assert_eq!(
            convert_to_expected_type(resp2_cluster_response.clone(), conversion_type),
            Ok(resp3_cluster_response.clone())
        );
        // resp3 -> resp3 conversion with non-empty `running_script` block
        assert_eq!(
            convert_to_expected_type(
                resp3_response_with_non_empty_first_part.clone(),
                conversion_type
            ),
            Ok(resp3_response_with_non_empty_first_part.clone())
        );
        // resp3 -> resp3 conversion with empty `running_script` block
        assert_eq!(
            convert_to_expected_type(
                resp3_response_with_empty_first_part.clone(),
                conversion_type
            ),
            Ok(resp3_response_with_empty_first_part.clone())
        );
        // resp3 -> resp3 cluster response
        assert_eq!(
            convert_to_expected_type(resp3_cluster_response.clone(), conversion_type),
            Ok(resp3_cluster_response.clone())
        );
    }

    #[test]
    fn convert_smismember() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("SMISMEMBER").arg("key").arg("elem")),
            Some(ExpectedReturnType::ArrayOfBools)
        ));

        let response = Value::Array(vec![Value::Int(0), Value::Int(1)]);
        let converted_response =
            convert_to_expected_type(response, Some(ExpectedReturnType::ArrayOfBools)).unwrap();
        let expected_response = Value::Array(vec![Value::Boolean(false), Value::Boolean(true)]);
        assert_eq!(expected_response, converted_response);
    }

    #[test]
    fn convert_to_array_of_pairs_return_type() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("HRANDFIELD")
                    .arg("key")
                    .arg("1")
                    .arg("withvalues")
            ),
            Some(ExpectedReturnType::ArrayOfPairs)
        ));

        assert!(expected_type_for_cmd(redis::cmd("HRANDFIELD").arg("key").arg("1")).is_none());
        assert!(expected_type_for_cmd(redis::cmd("HRANDFIELD").arg("key")).is_none());

        let flat_array = Value::Array(vec![
            Value::BulkString(b"key1".to_vec()),
            Value::BulkString(b"value1".to_vec()),
            Value::BulkString(b"key2".to_vec()),
            Value::BulkString(b"value2".to_vec()),
        ]);
        let two_dimensional_array = Value::Array(vec![
            Value::Array(vec![
                Value::BulkString(b"key1".to_vec()),
                Value::BulkString(b"value1".to_vec()),
            ]),
            Value::Array(vec![
                Value::BulkString(b"key2".to_vec()),
                Value::BulkString(b"value2".to_vec()),
            ]),
        ]);
        let converted_flat_array =
            convert_to_expected_type(flat_array, Some(ExpectedReturnType::ArrayOfPairs)).unwrap();
        assert_eq!(two_dimensional_array, converted_flat_array);

        let converted_two_dimensional_array = convert_to_expected_type(
            two_dimensional_array.clone(),
            Some(ExpectedReturnType::ArrayOfPairs),
        )
        .unwrap();
        assert_eq!(two_dimensional_array, converted_two_dimensional_array);

        let empty_array = Value::Array(vec![]);
        let converted_empty_array =
            convert_to_expected_type(empty_array.clone(), Some(ExpectedReturnType::ArrayOfPairs))
                .unwrap();
        assert_eq!(empty_array, converted_empty_array);

        let flat_array_unexpected_length =
            Value::Array(vec![Value::BulkString(b"somekey".to_vec())]);
        assert!(convert_to_expected_type(
            flat_array_unexpected_length,
            Some(ExpectedReturnType::ArrayOfPairs)
        )
        .is_err());
    }

    #[test]
    fn convert_zmpop_response() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("BZMPOP").arg(1).arg(1).arg("key").arg("min")),
            Some(ExpectedReturnType::ZMPopReturnType)
        ));
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZMPOP").arg(1).arg(1).arg("key").arg("min")),
            Some(ExpectedReturnType::ZMPopReturnType)
        ));

        let response = Value::Array(vec![
            Value::SimpleString("key".into()),
            Value::Array(vec![
                Value::Array(vec![Value::SimpleString("elem1".into()), Value::Double(1.)]),
                Value::Array(vec![Value::SimpleString("elem2".into()), Value::Double(2.)]),
            ]),
        ]);
        let converted_response =
            convert_to_expected_type(response, Some(ExpectedReturnType::ZMPopReturnType)).unwrap();
        let expected_response = Value::Array(vec![
            Value::SimpleString("key".into()),
            Value::Map(vec![
                (Value::BulkString("elem1".into()), Value::Double(1.)),
                (Value::BulkString("elem2".into()), Value::Double(2.)),
            ]),
        ]);
        assert_eq!(expected_response, converted_response);

        let response = Value::Nil;
        let converted_response =
            convert_to_expected_type(response.clone(), Some(ExpectedReturnType::ZMPopReturnType))
                .unwrap();
        assert_eq!(response, converted_response);
    }

    #[test]
    fn convert_to_member_score_pairs_return_type() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZRANDMEMBER")
                    .arg("key")
                    .arg("1")
                    .arg("withscores")
            ),
            Some(ExpectedReturnType::ArrayOfMemberScorePairs)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZRANDMEMBER").arg("key").arg("1")).is_none());
        assert!(expected_type_for_cmd(redis::cmd("ZRANDMEMBER").arg("key")).is_none());

        // convert_to_array_of_pairs_return_type already tests most functionality since the conversion for ArrayOfPairs
        // and ArrayOfMemberScorePairs is mostly the same. Here we also test that the scores are converted to double
        // when the server response was a RESP2 flat array.
        let flat_array = Value::Array(vec![
            Value::BulkString(b"one".to_vec()),
            Value::BulkString(b"1.0".to_vec()),
            Value::BulkString(b"two".to_vec()),
            Value::BulkString(b"2.0".to_vec()),
        ]);
        let expected_response = Value::Array(vec![
            Value::Array(vec![Value::BulkString(b"one".to_vec()), Value::Double(1.0)]),
            Value::Array(vec![Value::BulkString(b"two".to_vec()), Value::Double(2.0)]),
        ]);
        let converted_flat_array = convert_to_expected_type(
            flat_array,
            Some(ExpectedReturnType::ArrayOfMemberScorePairs),
        )
        .unwrap();
        assert_eq!(expected_response, converted_flat_array);
    }

    #[test]
    fn convert_to_array_of_string_and_array_return_type() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("LMPOP").arg("1").arg("key").arg("LEFT")),
            Some(ExpectedReturnType::ArrayOfStringAndArrays)
        ));

        // testing value conversion
        let flat_array = Value::Array(vec![
            Value::BulkString(b"1".to_vec()),
            Value::Array(vec![Value::BulkString(b"one".to_vec())]),
        ]);
        let expected_response = Value::Map(vec![(
            Value::BulkString("1".into()),
            Value::Array(vec![Value::BulkString(b"one".to_vec())]),
        )]);
        let converted_flat_array =
            convert_to_expected_type(flat_array, Some(ExpectedReturnType::ArrayOfStringAndArrays))
                .unwrap();
        assert_eq!(expected_response, converted_flat_array);
    }

    #[test]
    fn convert_zadd_only_if_incr_is_included() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("zadd")
                    .arg("XT")
                    .arg("CH")
                    .arg("incr")
                    .arg("0.6")
                    .arg("foo")
            ),
            Some(ExpectedReturnType::DoubleOrNull)
        ));

        assert!(expected_type_for_cmd(
            redis::cmd("zadd").arg("XT").arg("CH").arg("0.6").arg("foo")
        )
        .is_none());
    }

    #[test]
    fn convert_zrange_zdiff_only_if_withsocres_is_included() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("zrange").arg("0").arg("-1").arg("withscores")),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZRANGE").arg("0").arg("-1")).is_none());

        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZDIFF").arg("1").arg("withscores")),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZDIFF").arg("1")).is_none());
    }

    #[test]
    fn convert_zunion_only_if_withscores_is_included() {
        // Test ZUNION without options
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZUNION")
                    .arg("2")
                    .arg("set1")
                    .arg("set2")
                    .arg("WITHSCORES")
            ),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(
            expected_type_for_cmd(redis::cmd("ZUNION").arg("2").arg("set1").arg("set2")).is_none()
        );

        // Test ZUNION with Weights
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZUNION")
                    .arg("2")
                    .arg("set1")
                    .arg("set2")
                    .arg("WEIGHTS")
                    .arg("1")
                    .arg("2")
                    .arg("WITHSCORES")
            ),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(
            redis::cmd("ZUNION")
                .arg("2")
                .arg("set1")
                .arg("set2")
                .arg("WEIGHTS")
                .arg("1")
                .arg("2")
        )
        .is_none());

        // Test ZUNION with Aggregate
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZUNION")
                    .arg("2")
                    .arg("set1")
                    .arg("set2")
                    .arg("AGGREGATE")
                    .arg("MAX")
                    .arg("WITHSCORES")
            ),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(
            redis::cmd("ZUNION")
                .arg("2")
                .arg("set1")
                .arg("set2")
                .arg("AGGREGATE")
                .arg("MAX")
        )
        .is_none());

        // Test ZUNION with Weights and Aggregate
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZUNION")
                    .arg("2")
                    .arg("set1")
                    .arg("set2")
                    .arg("WEIGHTS")
                    .arg("1")
                    .arg("2")
                    .arg("AGGREGATE")
                    .arg("MAX")
                    .arg("WITHSCORES")
            ),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(
            redis::cmd("ZUNION")
                .arg("2")
                .arg("set1")
                .arg("set2")
                .arg("WEIGHTS")
                .arg("1")
                .arg("2")
                .arg("AGGREGATE")
                .arg("MAX")
        )
        .is_none());
    }

    #[test]
    fn zpopmin_zpopmax_return_type() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZPOPMIN").arg("1")),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZPOPMAX").arg("1")),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));
    }

    #[test]
    fn convert_bzpopmin_bzpopmax() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("BZPOPMIN")
                    .arg("myzset1")
                    .arg("myzset2")
                    .arg("1")
            ),
            Some(ExpectedReturnType::KeyWithMemberAndScore)
        ));

        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("BZPOPMAX")
                    .arg("myzset1")
                    .arg("myzset2")
                    .arg("1")
            ),
            Some(ExpectedReturnType::KeyWithMemberAndScore)
        ));

        let array_with_double_score = Value::Array(vec![
            Value::BulkString(b"key1".to_vec()),
            Value::BulkString(b"member1".to_vec()),
            Value::Double(2.0),
        ]);
        let result = convert_to_expected_type(
            array_with_double_score.clone(),
            Some(ExpectedReturnType::KeyWithMemberAndScore),
        )
        .unwrap();
        assert_eq!(array_with_double_score, result);

        let array_with_string_score = Value::Array(vec![
            Value::BulkString(b"key1".to_vec()),
            Value::BulkString(b"member1".to_vec()),
            Value::BulkString(b"2.0".to_vec()),
        ]);
        let result = convert_to_expected_type(
            array_with_string_score.clone(),
            Some(ExpectedReturnType::KeyWithMemberAndScore),
        )
        .unwrap();
        assert_eq!(array_with_double_score, result);

        let converted_nil_value =
            convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::KeyWithMemberAndScore))
                .unwrap();
        assert_eq!(Value::Nil, converted_nil_value);

        let array_with_unexpected_length = Value::Array(vec![
            Value::BulkString(b"key1".to_vec()),
            Value::BulkString(b"member1".to_vec()),
            Value::Double(2.0),
            Value::Double(2.0),
        ]);
        assert!(convert_to_expected_type(
            array_with_unexpected_length,
            Some(ExpectedReturnType::KeyWithMemberAndScore)
        )
        .is_err());
    }

    #[test]
    fn convert_zank_zrevrank_only_if_withsocres_is_included() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("zrank")
                    .arg("key")
                    .arg("member")
                    .arg("withscore")
            ),
            Some(ExpectedReturnType::ZRankReturnType)
        ));

        assert!(expected_type_for_cmd(redis::cmd("zrank").arg("key").arg("member")).is_none());

        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZREVRANK")
                    .arg("key")
                    .arg("member")
                    .arg("withscore")
            ),
            Some(ExpectedReturnType::ZRankReturnType)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZREVRANK").arg("key").arg("member")).is_none());
    }

    #[test]
    fn convert_zmscore() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZMSCORE").arg("key").arg("member")),
            Some(ExpectedReturnType::ArrayOfDoubleOrNull)
        ));

        let array_response = Value::Array(vec![
            Value::Nil,
            Value::Double(1.5),
            Value::BulkString(b"2.5".to_vec()),
        ]);
        let converted_response = convert_to_expected_type(
            array_response,
            Some(ExpectedReturnType::ArrayOfDoubleOrNull),
        )
        .unwrap();
        let expected_response =
            Value::Array(vec![Value::Nil, Value::Double(1.5), Value::Double(2.5)]);
        assert_eq!(expected_response, converted_response);

        let unexpected_response_type = Value::Double(0.5);
        assert!(convert_to_expected_type(
            unexpected_response_type,
            Some(ExpectedReturnType::ArrayOfDoubleOrNull)
        )
        .is_err());
    }

    #[test]
    fn convert_smove_to_bool() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("SMOVE").arg("key1").arg("key2").arg("elem")),
            Some(ExpectedReturnType::Boolean)
        ));
    }

    #[test]
    fn test_convert_to_map_of_string_to_double() {
        assert_eq!(
            convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::MapOfStringToDouble)),
            Ok(Value::Nil)
        );
        let unconverted_map = vec![
            (
                Value::BulkString(b"key1".to_vec()),
                Value::BulkString(b"10.5".to_vec()),
            ),
            (
                Value::BulkString(b"key2".to_vec()),
                Value::BulkString(b"20.8".to_vec()),
            ),
            (Value::Double(20.5), Value::BulkString(b"30.2".to_vec())),
        ];

        let converted_map = convert_to_expected_type(
            Value::Map(unconverted_map),
            Some(ExpectedReturnType::MapOfStringToDouble),
        )
        .unwrap();

        let converted_map = if let Value::Map(map) = converted_map {
            map
        } else {
            panic!("Expected a Map, but got {:?}", converted_map);
        };

        assert_eq!(converted_map.len(), 3);

        let (key, value) = &converted_map[0];
        assert_eq!(*key, Value::BulkString(b"key1".to_vec()));
        assert_eq!(*value, Value::Double(10.5));

        let (key, value) = &converted_map[1];
        assert_eq!(*key, Value::BulkString(b"key2".to_vec()));
        assert_eq!(*value, Value::Double(20.8));

        let (key, value) = &converted_map[2];
        assert_eq!(*key, Value::BulkString(b"20.5".to_vec()));
        assert_eq!(*value, Value::Double(30.2));

        let array_of_arrays = vec![
            Value::Array(vec![
                Value::BulkString(b"key1".to_vec()),
                Value::BulkString(b"10.5".to_vec()),
            ]),
            Value::Array(vec![
                Value::BulkString(b"key2".to_vec()),
                Value::Double(20.5),
            ]),
        ];

        let converted_map = convert_to_expected_type(
            Value::Array(array_of_arrays),
            Some(ExpectedReturnType::MapOfStringToDouble),
        )
        .unwrap();

        let converted_map = if let Value::Map(map) = converted_map {
            map
        } else {
            panic!("Expected a Map, but got {:?}", converted_map);
        };

        assert_eq!(converted_map.len(), 2);

        let (key, value) = &converted_map[0];
        assert_eq!(*key, Value::BulkString(b"key1".to_vec()));
        assert_eq!(*value, Value::Double(10.5));

        let (key, value) = &converted_map[1];
        assert_eq!(*key, Value::BulkString(b"key2".to_vec()));
        assert_eq!(*value, Value::Double(20.5));

        let array_of_arrays_err: Vec<Value> = vec![Value::Array(vec![
            Value::BulkString(b"key".to_vec()),
            Value::BulkString(b"value".to_vec()),
            Value::BulkString(b"10.5".to_vec()),
        ])];

        assert!(convert_to_expected_type(
            Value::Array(array_of_arrays_err),
            Some(ExpectedReturnType::MapOfStringToDouble)
        )
        .is_err());
    }

    #[test]
    fn test_convert_to_zrank_return_type() {
        assert_eq!(
            convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::ZRankReturnType)),
            Ok(Value::Nil)
        );

        let array = vec![
            Value::BulkString(b"key".to_vec()),
            Value::BulkString(b"20.5".to_vec()),
        ];

        let array_result = convert_to_expected_type(
            Value::Array(array),
            Some(ExpectedReturnType::ZRankReturnType),
        )
        .unwrap();

        let array_result = if let Value::Array(array) = array_result {
            array
        } else {
            panic!("Expected an Array, but got {:?}", array_result);
        };
        assert_eq!(array_result.len(), 2);

        assert_eq!(array_result[0], Value::BulkString(b"key".to_vec()));
        assert_eq!(array_result[1], Value::Double(20.5));

        let array_err = vec![Value::BulkString(b"key".to_vec())];
        assert!(convert_to_expected_type(
            Value::Array(array_err),
            Some(ExpectedReturnType::ZRankReturnType)
        )
        .is_err());
    }
    #[test]
    fn pass_null_value_for_double_or_null() {
        assert_eq!(
            convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::DoubleOrNull)),
            Ok(Value::Nil)
        );

        assert!(convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::Double)).is_err());
    }

    #[test]
    fn test_convert_to_list_of_bool_or_null() {
        let array = vec![Value::Nil, Value::Int(0), Value::Int(1)];
        let array_result = convert_to_expected_type(
            Value::Array(array),
            Some(ExpectedReturnType::JsonToggleReturnType),
        )
        .unwrap();

        let array_result = if let Value::Array(array) = array_result {
            array
        } else {
            panic!("Expected an Array, but got {:?}", array_result);
        };
        assert_eq!(array_result.len(), 3);

        assert_eq!(array_result[0], Value::Nil);
        assert_eq!(array_result[1], Value::Boolean(false));
        assert_eq!(array_result[2], Value::Boolean(true));

        assert!(convert_to_expected_type(
            Value::Nil,
            Some(ExpectedReturnType::JsonToggleReturnType)
        )
        .is_err());
    }

    #[test]
    fn test_convert_spop_to_set_for_spop_count() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("SPOP").arg("key1").arg("3")),
            Some(ExpectedReturnType::Set)
        ));
        assert!(expected_type_for_cmd(redis::cmd("SPOP").arg("key1")).is_none());
    }
    #[test]
    fn test_convert_to_geo_search_return_type() {
        let array = Value::Array(vec![
            Value::Array(vec![
                Value::BulkString(b"name1".to_vec()),
                Value::BulkString(b"1.23".to_vec()), // dist (float)
                Value::Int(123456),                  // hash (int)
                Value::Array(vec![
                    Value::BulkString(b"10.0".to_vec()), // lon (float)
                    Value::BulkString(b"20.0".to_vec()), // lat (float)
                ]),
            ]),
            Value::Array(vec![
                Value::BulkString(b"name2".to_vec()),
                Value::BulkString(b"2.34".to_vec()), // dist (float)
                Value::Int(654321),                  // hash (int)
                Value::Array(vec![
                    Value::BulkString(b"30.0".to_vec()), // lon (float)
                    Value::BulkString(b"40.0".to_vec()), // lat (float)
                ]),
            ]),
        ]);

        // Expected output value after conversion
        let expected_result = Value::Array(vec![
            Value::Array(vec![
                Value::BulkString(b"name1".to_vec()),
                Value::Array(vec![
                    Value::Double(1.23), // dist (float)
                    Value::Int(123456),  // hash (int)
                    Value::Array(vec![
                        Value::Double(10.0), // lon (float)
                        Value::Double(20.0), // lat (float)
                    ]),
                ]),
            ]),
            Value::Array(vec![
                Value::BulkString(b"name2".to_vec()),
                Value::Array(vec![
                    Value::Double(2.34), // dist (float)
                    Value::Int(654321),  // hash (int)
                    Value::Array(vec![
                        Value::Double(30.0), // lon (float)
                        Value::Double(40.0), // lat (float)
                    ]),
                ]),
            ]),
        ]);

        let result =
            convert_to_expected_type(array.clone(), Some(ExpectedReturnType::GeoSearchReturnType))
                .unwrap();
        assert_eq!(result, expected_result);
    }
    #[test]
    fn test_geosearch_return_type() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("GEOSEARCH")
                    .arg("WITHDIST")
                    .arg("WITHHASH")
                    .arg("WITHCOORD")
            ),
            Some(ExpectedReturnType::GeoSearchReturnType)
        ));

        assert!(matches!(
            expected_type_for_cmd(redis::cmd("GEOSEARCH").arg("WITHDIST").arg("WITHHASH")),
            Some(ExpectedReturnType::GeoSearchReturnType)
        ));

        assert!(matches!(
            expected_type_for_cmd(redis::cmd("GEOSEARCH").arg("WITHDIST")),
            Some(ExpectedReturnType::GeoSearchReturnType)
        ));

        assert!(expected_type_for_cmd(redis::cmd("GEOSEARCH").arg("key")).is_none());
    }
    #[test]
    fn convert_lcs_idx() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("LCS").arg("key1").arg("key2").arg("IDX")),
            Some(ExpectedReturnType::Map {
                key_type: &Some(ExpectedReturnType::SimpleString),
                value_type: &None,
            })
        ));
    }
}
