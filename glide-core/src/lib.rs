/*
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

#[cfg(feature = "socket-layer")]
include!(concat!(env!("OUT_DIR"), "/protobuf/mod.rs"));
pub mod client;
mod retry_strategies;
#[cfg(feature = "socket-layer")]
pub mod rotating_buffer;
#[cfg(feature = "socket-layer")]
mod socket_listener;
#[cfg(feature = "socket-layer")]
pub use socket_listener::*;
pub mod errors;
pub mod scripts_container;
pub use client::ConnectionRequest;
pub mod cluster_scan_container;
pub mod request_type;
