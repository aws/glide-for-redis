use bytes::BytesMut;
/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use logger_core::log_info;
use once_cell::sync::Lazy;
use sha1_smol::Sha1;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};

static CONTAINER: Lazy<Mutex<HashMap<String, Arc<BytesMut>>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

pub fn add_script(script: &[u8]) -> String {
    let mut hash = Sha1::new();
    hash.update(script);
    let hash = hash.digest().to_string();
    log_info(
        "script lifetime",
        format!("Added script with hash: `{hash}`"),
    );
    CONTAINER
        .lock()
        .unwrap()
        .insert(hash.clone(), Arc::new(script.into()));
    hash
}

pub fn get_script(hash: &str) -> Option<Arc<BytesMut>> {
    CONTAINER.lock().unwrap().get(hash).cloned()
}

pub fn remove_script(hash: &str) {
    log_info(
        "script lifetime",
        format!("Removed script with hash: `{hash}`"),
    );
    CONTAINER.lock().unwrap().remove(hash);
}
