from glide.config import BaseClientConfiguration, NodeAddress, ReadFrom, TlsMode
from glide.protobuf.connection_request_pb2 import ConnectionRequest
from glide.protobuf.connection_request_pb2 import ReadFrom as ProtobufReadFrom
from glide.protobuf.connection_request_pb2 import TlsMode as ProtobufTlsMode


def test_default_client_config():
    config = BaseClientConfiguration([])
    assert len(config.addresses) == 0
    assert config.read_from.value == ProtobufReadFrom.Primary
    assert config.tls_mode is None
    assert config.client_name is None


def test_convert_to_protobuf():
    config = BaseClientConfiguration(
        [NodeAddress("127.0.0.1")],
        tls_mode=TlsMode.Auto,
        read_from=ReadFrom.PREFER_REPLICA,
        client_name="TEST_CLIENT_NAME",
    )
    request = config._create_a_protobuf_conn_request()
    assert isinstance(request, ConnectionRequest)
    assert request.addresses[0].host == "127.0.0.1"
    assert request.addresses[0].port == 6379
    assert request.tls_mode is ProtobufTlsMode.SecureTls
    assert request.read_from == ProtobufReadFrom.PreferReplica
    assert request.client_name == "TEST_CLIENT_NAME"
