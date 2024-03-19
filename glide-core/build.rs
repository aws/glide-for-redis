/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

#[cfg(feature = "socket-layer")]
fn build_protobuf() {
    let customization_options = protobuf_codegen::Customize::default()
        .lite_runtime(false)
        .tokio_bytes(true)
        .tokio_bytes_for_string(true);
    protobuf_codegen::Codegen::new()
        .cargo_out_dir("protobuf")
        .include("src")
        .input("src/protobuf/redis_request.proto")
        .input("src/protobuf/response.proto")
        .input("src/protobuf/connection_request.proto")
        .customize(customization_options)
        .run_from_script();
}

fn main() {
    #[cfg(feature = "socket-layer")]
    build_protobuf();
}
