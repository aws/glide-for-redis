# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import pytest
from glide.protobuf.redis_request_pb2 import RedisRequest, RequestType
from glide.protobuf.response_pb2 import Response
from glide.protobuf_codec import PartialMessageException, ProtobufCodec


class TestProtobufCodec:
    def test_encode_decode_delimited(self):
        request = RedisRequest()
        request.callback_idx = 1
        request.single_command.request_type = RequestType.Set
        args = [
            "foo",
            "bar",
        ]

        bytes_args = [bytes(elem, encoding="utf8") for elem in args]
        request.single_command.args_array.args[:] = bytes_args
        b_arr = bytearray()
        ProtobufCodec.encode_delimited(b_arr, request)
        msg_len_varint = int(b_arr[0])
        assert msg_len_varint == 18
        assert len(b_arr) == msg_len_varint + 1
        offset = 0
        b_arr_view = memoryview(b_arr)
        parsed_request, new_offset = ProtobufCodec.decode_delimited(
            b_arr, b_arr_view, offset, RedisRequest
        )
        assert new_offset == len(b_arr)
        assert parsed_request.callback_idx == 1
        assert parsed_request.single_command.request_type == RequestType.Set
        assert parsed_request.single_command.args_array.args == bytes_args

    def test_decode_partial_message_fails(self):
        response = Response()
        response.callback_idx = 1
        b_arr = bytearray()
        ProtobufCodec.encode_delimited(b_arr, response)
        b_arr_view = memoryview(b_arr)
        with pytest.raises(PartialMessageException):
            ProtobufCodec.decode_delimited(b_arr[:1], b_arr_view[:1], 0, Response)

    def test_decode_partial_varint_fails(self):
        varint = ProtobufCodec._varint_bytes(10000000)
        b_arr_view = memoryview(varint)
        assert len(varint) > 1
        with pytest.raises(PartialMessageException):
            ProtobufCodec.decode_delimited(varint[:1], b_arr_view[:1], 0, Response)

    def test_encode_decode_varint(self):
        value = 10000000
        varint = ProtobufCodec._varint_bytes(value)
        decoded_varint, res_len = ProtobufCodec._decode_varint_32(varint, 0)
        assert res_len == len(varint)
        assert decoded_varint == value
