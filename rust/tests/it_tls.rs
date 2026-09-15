// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! TLS integration tests.
//!
//! TODO #6996: standalone (`GlideClient`) TLS coverage

mod common;

use common::ClusterHarness;
use glide::{
    ConnectionManagementCommands, GlideClusterClient, GlideClusterClientConfiguration, TlsConfig,
};

timed_tokio_test!(
    async fn tls_cluster() {
        let server = ClusterHarness::start_tls();
        assert_connected(server.client_with_tls().await).await;
    }
);

timed_tokio_test!(
    async fn insecure_tls_cluster() {
        let server = ClusterHarness::start_tls();
        assert_connected(server.client_with_insecure_tls().await).await;
    }
);

timed_tokio_test!(
    async fn tls_cluster_untrusted() {
        let server = ClusterHarness::start_tls();
        let untrusted_config = server.config().tls(TlsConfig::SecureTls);
        assert_does_not_connect(untrusted_config).await;
    }
);

timed_tokio_test!(
    async fn mtls_cluster() {
        let server = ClusterHarness::start_tls_mtls();
        assert_connected(server.client_with_mtls().await).await;
    }
);

timed_tokio_test!(
    async fn mtls_cluster_missing_client_cert() {
        let server = ClusterHarness::start_tls_mtls();
        let missing_client_cert_config = server.config_with_tls();
        assert_does_not_connect(missing_client_cert_config).await;
    }
);

// Asserts that the given client is connected.
async fn assert_connected(client: GlideClusterClient) {
    assert_eq!(client.ping().await.unwrap(), "PONG");
}

// Asserts that a client does not connect with the given configuration.
async fn assert_does_not_connect(config: GlideClusterClientConfiguration) {
    match GlideClusterClient::connect(config).await {
        Err(_) => {} // expected
        Ok(client) => {
            let res = client.ping().await;
            assert!(
                res.is_err(),
                "untrusted/invalid TLS config must not allow cluster commands"
            );
        }
    }
}
