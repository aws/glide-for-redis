# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import struct
from typing import List, Tuple, Type

from google.protobuf import message

"""
Codec for protobuf messages.
All of the Varint functions were copied from https://github.com/protocolbuffers/protobuf
"""


class ProtobufCodec:
    @classmethod
    def _decode_varint_32(cls, buffer, pos):
        decoder_func = cls._varint_decoder((1 << 32) - 1, int)
        return decoder_func(buffer, pos)

    @staticmethod
    def _varint_decoder(mask, result_type):
        """Return an encoder for a basic varint value (does not include tag).

        Decoded values will be bitwise-anded with the given mask before being
        returned, e.g. to limit them to 32 bits.  The returned decoder does not
        take the usual "end" parameter -- the caller is expected to do bounds checking
        after the fact (often the caller can defer such checking until later).  The
        decoder returns a (value, new_pos) pair.
        """

        def decode_varint(buffer, pos):
            result = 0
            shift = 0
            while 1:
                b = buffer[pos]
                result |= (b & 0x7F) << shift
                pos += 1
                if not (b & 0x80):
                    result &= mask
                    result = result_type(result)
                    return (result, pos)
                shift += 7
                if shift >= 64:
                    raise message.DecodeError("Too many bytes when decoding varint.")

        return decode_varint

    @staticmethod
    def _varint_encoder():
        """Return an encoder for a basic varint value (does not include tag)."""

        local_int2byte = struct.Struct(">B").pack

        def encode_varint(write, value):
            bits = value & 0x7F
            value >>= 7
            while value:
                write(local_int2byte(0x80 | bits))
                bits = value & 0x7F
                value >>= 7
            return write(local_int2byte(bits))

        return encode_varint

    @classmethod
    def _varint_bytes(cls, value: int) -> bytes:
        """Encode the given integer as a varint and return the bytes."""

        pieces: List[bytes] = []
        func = cls._varint_encoder()
        func(pieces.append, value)
        return b"".join(pieces)

    @classmethod
    def decode_delimited(
        cls,
        read_bytes: bytearray,
        read_bytes_view: memoryview,
        offset: int,
        message_class: Type[message.Message],
    ) -> Tuple[message.Message, int]:
        try:
            msg_len, new_pos = cls._decode_varint_32(read_bytes_view, offset)
        except IndexError:
            # Didn't read enough bytes to decode the varint
            raise PartialMessageException(
                "Didn't read enough bytes to decode the varint"
            )
        required_read_size = new_pos + msg_len
        if required_read_size > len(read_bytes):
            # Recieved only partial response
            raise PartialMessageException("Recieved only a partial response")
        offset = new_pos
        msg_buf = read_bytes_view[offset : offset + msg_len]
        offset += msg_len
        message = message_class()
        message.ParseFromString(msg_buf)
        return (message, offset)

    @classmethod
    def encode_delimited(cls, b_arr: bytearray, message: message.Message) -> None:
        bytes_request = message.SerializeToString()
        varint = cls._varint_bytes(len(bytes_request))
        b_arr.extend(varint)
        b_arr.extend(bytes_request)


class PartialMessageException(Exception):
    pass
