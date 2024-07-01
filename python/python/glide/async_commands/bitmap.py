# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from abc import ABC, abstractmethod
from enum import Enum
from typing import List, Optional


class BitmapIndexType(Enum):
    """
    Enumeration specifying if index arguments are BYTE indexes or BIT indexes. Can be specified in `OffsetOptions`,
    which is an optional argument to the `BITCOUNT` command.

    Since: Redis version 7.0.0.
    """

    BYTE = "BYTE"
    """
    Specifies that indexes provided to `OffsetOptions` are byte indexes.
    """
    BIT = "BIT"
    """
    Specifies that indexes provided to `OffsetOptions` are bit indexes.
    """


class OffsetOptions:
    def __init__(
        self, start: int, end: int, index_type: Optional[BitmapIndexType] = None
    ):
        """
        Represents offsets specifying a string interval to analyze in the `BITCOUNT` command. The offsets are
        zero-based indexes, with `0` being the first index of the string, `1` being the next index and so on.
        The offsets can also be negative numbers indicating offsets starting at the end of the string, with `-1` being
        the last index of the string, `-2` being the penultimate, and so on.

        Args:
            start (int): The starting offset index.
            end (int): The ending offset index.
            index_type (Optional[BitmapIndexType]): The index offset type. This option can only be specified if you are
                using Redis version 7.0.0 or above. Could be either `BitmapIndexType.BYTE` or `BitmapIndexType.BIT`.
                If no index type is provided, the indexes will be assumed to be byte indexes.
        """
        self.start = start
        self.end = end
        self.index_type = index_type

    def to_args(self) -> List[str]:
        args = [str(self.start), str(self.end)]
        if self.index_type is not None:
            args.append(self.index_type.value)

        return args


class BitwiseOperation(Enum):
    """
    Enumeration defining the bitwise operation to use in the `BITOP` command. Specifies the bitwise operation to
    perform between the passed in keys.
    """

    AND = "AND"
    OR = "OR"
    XOR = "XOR"
    NOT = "NOT"


class BitEncoding(ABC):
    """
    Abstract Base Class used to specify a signed or unsigned argument encoding for the `BITFIELD` or `BITFIELD_RO`
    commands.
    """

    @abstractmethod
    def to_arg(self) -> str:
        """
        Returns the encoding as a string argument to be used in the `BITFIELD` or `BITFIELD_RO`
        commands.
        """
        pass


class SignedEncoding(BitEncoding):
    # Prefix specifying that the encoding is signed.
    SIGNED_ENCODING_PREFIX = "i"

    def __init__(self, encoding_length: int):
        """
        Represents a signed argument encoding. Must be less than 65 bits long.

        Args:
            encoding_length (int): The bit size of the encoding.
        """
        self._encoding = f"{self.SIGNED_ENCODING_PREFIX}{str(encoding_length)}"

    def to_arg(self) -> str:
        return self._encoding


class UnsignedEncoding(BitEncoding):
    # Prefix specifying that the encoding is unsigned.
    UNSIGNED_ENCODING_PREFIX = "u"

    def __init__(self, encoding_length: int):
        """
        Represents an unsigned argument encoding. Must be less than 64 bits long.

        Args:
            encoding_length (int): The bit size of the encoding.
        """
        self._encoding = f"{self.UNSIGNED_ENCODING_PREFIX}{str(encoding_length)}"

    def to_arg(self) -> str:
        return self._encoding


class BitFieldOffset(ABC):
    """Abstract Base Class representing an offset for an array of bits for the `BITFIELD` or `BITFIELD_RO` commands."""

    @abstractmethod
    def to_arg(self) -> str:
        """
        Returns the offset as a string argument to be used in the `BITFIELD` or `BITFIELD_RO`
        commands.
        """
        pass


class BitOffset(BitFieldOffset):
    def __init__(self, offset: int):
        """
        Represents an offset in an array of bits for the `BITFIELD` or `BITFIELD_RO` commands. Must be greater than or
        equal to 0.

        For example, if we have the binary `01101001` with offset of 1 for an unsigned encoding of size 4, then the value
        is 13 from `0(1101)001`.

        Args:
            offset (int): The bit index offset in the array of bits.
        """
        self._offset = str(offset)

    def to_arg(self) -> str:
        return self._offset


class BitOffsetMultiplier(BitFieldOffset):
    # Prefix specifying that the offset uses an encoding multiplier.
    OFFSET_MULTIPLIER_PREFIX = "#"

    def __init__(self, offset: int):
        """
        Represents an offset in an array of bits for the `BITFIELD` or `BITFIELD_RO` commands. The bit offset index is
        calculated as the numerical value of the offset multiplied by the encoding value. Must be greater than or equal
        to 0.

        For example, if we have the binary 01101001 with offset multiplier of 1 for an unsigned encoding of size 4, then
        the value is 9 from `0110(1001)`.

        Args:
            offset (int): The offset in the array of bits, which will be multiplied by the encoding value to get the
            final bit index offset.
        """
        self._offset = f"{self.OFFSET_MULTIPLIER_PREFIX}{str(offset)}"

    def to_arg(self) -> str:
        return self._offset


class BitFieldSubCommands(ABC):
    """Abstract Base Class representing subcommands for the `BITFIELD` or `BITFIELD_RO` commands."""

    @abstractmethod
    def to_args(self) -> List[str]:
        """
        Returns the subcommand as a list of string arguments to be used in the `BITFIELD` or `BITFIELD_RO` commands.
        """
        pass


class BitFieldGet(BitFieldSubCommands):
    # "GET" subcommand string for use in the `BITFIELD` or `BITFIELD_RO` commands.
    GET_COMMAND_STRING = "GET"

    def __init__(self, encoding: BitEncoding, offset: BitFieldOffset):
        """
        Represents the "GET" subcommand for getting a value in the binary representation of the string stored in `key`.

        Args:
            encoding (BitEncoding): The bit encoding for the subcommand.
            offset (BitFieldOffset): The offset in the array of bits from which to get the value.
        """
        self._encoding = encoding
        self._offset = offset

    def to_args(self) -> List[str]:
        return [self.GET_COMMAND_STRING, self._encoding.to_arg(), self._offset.to_arg()]


class BitFieldSet(BitFieldSubCommands):
    # "SET" subcommand string for use in the `BITFIELD` command.
    SET_COMMAND_STRING = "SET"

    def __init__(self, encoding: BitEncoding, offset: BitFieldOffset, value: int):
        """
        Represents the "SET" subcommand for setting bits in the binary representation of the string stored in `key`.

        Args:
            encoding (BitEncoding): The bit encoding for the subcommand.
            offset (BitOffset): The offset in the array of bits where the value will be set.
            value (int): The value to set the bits in the binary value to.
        """
        self._encoding = encoding
        self._offset = offset
        self._value = value

    def to_args(self) -> List[str]:
        return [
            self.SET_COMMAND_STRING,
            self._encoding.to_arg(),
            self._offset.to_arg(),
            str(self._value),
        ]


class BitFieldIncrBy(BitFieldSubCommands):
    # "INCRBY" subcommand string for use in the `BITFIELD` command.
    INCRBY_COMMAND_STRING = "INCRBY"

    def __init__(self, encoding: BitEncoding, offset: BitFieldOffset, increment: int):
        """
        Represents the "INCRBY" subcommand for increasing or decreasing bits in the binary representation of the
        string stored in `key`.

        Args:
            encoding (BitEncoding): The bit encoding for the subcommand.
            offset (BitOffset): The offset in the array of bits where the value will be incremented.
            increment (int): The value to increment the bits in the binary value by.
        """
        self._encoding = encoding
        self._offset = offset
        self._increment = increment

    def to_args(self) -> List[str]:
        return [
            self.INCRBY_COMMAND_STRING,
            self._encoding.to_arg(),
            self._offset.to_arg(),
            str(self._increment),
        ]


class BitOverflowControl(Enum):
    """
    Enumeration specifying bit overflow controls for the `BITFIELD` command.
    """

    WRAP = "WRAP"
    """
    Performs modulo when overflows occur with unsigned encoding. When overflows occur with signed encoding, the value 
    restarts at the most negative value. When underflows occur with signed encoding, the value restarts at the most 
    positive value.
    """
    SAT = "SAT"
    """
    Underflows remain set to the minimum value, and overflows remain set to the maximum value.
    """
    FAIL = "FAIL"
    """
    Returns `None` when overflows occur.
    """


class BitFieldOverflow(BitFieldSubCommands):
    # "OVERFLOW" subcommand string for use in the `BITFIELD` command.
    OVERFLOW_COMMAND_STRING = "OVERFLOW"

    def __init__(self, overflow_control: BitOverflowControl):
        """
        Represents the "OVERFLOW" subcommand that determines the result of the "SET" or "INCRBY" `BITFIELD` subcommands
        when an underflow or overflow occurs.

        Args:
            overflow_control (BitOverflowControl): The desired overflow behavior.
        """
        self._overflow_control = overflow_control

    def to_args(self) -> List[str]:
        return [self.OVERFLOW_COMMAND_STRING, self._overflow_control.value]


def _create_bitfield_args(subcommands: List[BitFieldSubCommands]) -> List[str]:
    args = []
    for subcommand in subcommands:
        args.extend(subcommand.to_args())

    return args


def _create_bitfield_read_only_args(
    subcommands: List[BitFieldGet],
) -> List[str]:
    args = []
    for subcommand in subcommands:
        args.extend(subcommand.to_args())

    return args
