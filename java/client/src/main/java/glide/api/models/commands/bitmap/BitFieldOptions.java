/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.bitmap;

import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.commands.BitmapBaseCommands;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Subcommand arguments for {@link BitmapBaseCommands#bitfield(String, BitFieldSubCommands[])} and
 * {@link BitmapBaseCommands#bitfieldReadOnly(String, BitFieldReadOnlySubCommands[])}. Specifies
 * subcommands, bit-encoding type, and offset type.
 *
 * @see <a href="https://redis.io/commands/bitfield/">redis.io</a> and <a
 *     href="https://redis.io/docs/latest/commands/bitfield_ro/">redis.io</a>
 */
public class BitFieldOptions {
    /** <code>GET</code> subcommand string to include in the <code>BITFIELD</code> command. */
    public static final String GET_COMMAND_STRING = "GET";

    /** <code>SET</code> subcommand string to include in the <code>BITFIELD</code> command. */
    public static final String SET_COMMAND_STRING = "SET";

    /** <code>INCRBY</code> subcommand string to include in the <code>BITFIELD</code> command. */
    public static final String INCRBY_COMMAND_STRING = "INCRBY";

    /** <code>OVERFLOW</code> subcommand string to include in the <code>BITFIELD</code> command. */
    public static final String OVERFLOW_COMMAND_STRING = "OVERFLOW";

    /** Prefix specifying that the encoding is unsigned. */
    public static final String UNSIGNED_ENCODING_PREFIX = "u";

    /** Prefix specifying that the encoding is signed. */
    public static final String SIGNED_ENCODING_PREFIX = "i";

    /** Prefix specifying that the offset uses an encoding multiplier. */
    public static final String OFFSET_MULTIPLIER_PREFIX = "#";

    /** Subcommands for <code>bitfield</code> and <code>bitfield_ro</code>. */
    public interface BitFieldSubCommands {
        /**
         * Creates the subcommand arguments.
         *
         * @return a String array with subcommands and their arguments.
         */
        String[] toArgs();
    }

    /** Subcommands for <code>bitfieldReadOnly</code>. */
    public interface BitFieldReadOnlySubCommands extends BitFieldSubCommands {}

    /**
     * <code>GET</code> subcommand for getting the value in the binary representation of the string
     * stored in <code>key</code> based on {@link BitEncoding} and {@link Offset}.
     */
    @RequiredArgsConstructor
    public static final class BitFieldGet implements BitFieldReadOnlySubCommands {
        /** Specifies if the bit encoding is signed or unsigned. */
        private final BitEncoding encoding;

        /** Specifies if the offset uses an encoding multiplier. */
        private final BitOffset offset;

        public String[] toArgs() {
            return new String[] {GET_COMMAND_STRING, encoding.getEncoding(), offset.getOffset()};
        }
    }

    /**
     * <code>SET</code> subcommand for setting the bits in the binary representation of the string
     * stored in <code>key</code> based on {@link BitEncoding} and {@link Offset}.
     */
    @RequiredArgsConstructor
    public static final class BitFieldSet implements BitFieldSubCommands {
        /** Specifies if the bit encoding is signed or unsigned. */
        private final BitEncoding encoding;

        /** Specifies if the offset uses an encoding multiplier. */
        private final BitOffset offset;

        /** Value to set the bits in the binary value to. */
        private final long value;

        public String[] toArgs() {
            return new String[] {
                SET_COMMAND_STRING, encoding.getEncoding(), offset.getOffset(), Long.toString(value)
            };
        }
    }

    /**
     * <code>INCRBY</code> subcommand for increasing or decreasing the bits in the binary
     * representation of the string stored in <code>key</code> based on {@link BitEncoding} and {@link
     * Offset}.
     */
    @RequiredArgsConstructor
    public static final class BitFieldIncrby implements BitFieldSubCommands {
        /** Specifies if the bit encoding is signed or unsigned. */
        private final BitEncoding encoding;

        /** Specifies if the offset uses an encoding multiplier. */
        private final BitOffset offset;

        /** Value to increment the bits in the binary value by. */
        private final long increment;

        public String[] toArgs() {
            return new String[] {
                INCRBY_COMMAND_STRING, encoding.getEncoding(), offset.getOffset(), Long.toString(increment)
            };
        }
    }

    /**
     * <code>OVERFLOW</code> subcommand that determines the result of the <code>SET</code> or <code>
     * INCRBY</code> commands when an under or overflow occurs.
     */
    @RequiredArgsConstructor
    public static final class BitFieldOverflow implements BitFieldSubCommands {
        /** Overflow behaviour. */
        private final BitOverflowControl overflowControl;

        public String[] toArgs() {
            return new String[] {OVERFLOW_COMMAND_STRING, overflowControl.toString()};
        }

        /** Supported bit overflow controls */
        public enum BitOverflowControl {
            /**
             * Performs modulo when overflow occurs with unsigned encoding. When overflows occur with
             * signed encoding, the value restarts at the most negative value. When underflows occur with
             * signed encoding, the value restarts at the most positive value.
             */
            WRAP,
            /**
             * Underflows remain set to the minimum value and overflows remain set to the maximum value.
             */
            SAT,
            /** Returns null when overflows occur. */
            FAIL
        }
    }

    /** Specifies if the argument is a signed or unsigned encoding */
    private interface BitEncoding {
        String getEncoding();
    }

    /** Specifies that the argument is a signed encoding. Must be less than 65 bits long. */
    public static final class SignedEncoding implements BitEncoding {
        @Getter private final String encoding;

        /**
         * Constructor that prepends the number with "i" to specify that it is in signed encoding.
         *
         * @param encodingLength bit size of encoding.
         */
        public SignedEncoding(long encodingLength) {
            encoding = SIGNED_ENCODING_PREFIX.concat(Long.toString(encodingLength));
        }
    }

    /** Specifies that the argument is an unsigned encoding. Must be less than 64 bits long. */
    public static final class UnsignedEncoding implements BitEncoding {
        @Getter private final String encoding;

        /**
         * Constructor that prepends the number with "u" to specify that it is in unsigned encoding.
         *
         * @param encodingLength bit size of encoding.
         */
        public UnsignedEncoding(long encodingLength) {
            encoding = UNSIGNED_ENCODING_PREFIX.concat(Long.toString(encodingLength));
        }
    }

    /** Offset in the array of bits. */
    private interface BitOffset {
        String getOffset();
    }

    /**
     * Offset in the array of bits. Must be greater than or equal to 0. If we have the binary 01101001
     * with offset of 1 for unsigned encoding of size 4, then the value is 13 from 0(1101)001.
     */
    public static final class Offset implements BitOffset {
        @Getter private final String offset;

        /**
         * Constructor for Offset.
         *
         * @param offset element offset in the array of bits.
         */
        public Offset(long offset) {
            this.offset = Long.toString(offset);
        }
    }

    /**
     * Offset in the array of bits multiplied by the encoding value. Must be greater than or equal to
     * 0. If we have the binary 01101001 with offset multiplier of 1 for unsigned encoding of size 4,
     * then the value is 9 from 0110(1001).
     */
    public static final class OffsetMultiplier implements BitOffset {
        @Getter private final String offset;

        /**
         * Constructor for the offset multiplier.
         *
         * @param offset element offset multiplied by the encoding value in the array of bits.
         */
        public OffsetMultiplier(long offset) {
            this.offset = OFFSET_MULTIPLIER_PREFIX.concat(Long.toString(offset));
        }
    }

    /**
     * Creates the arguments to be used in {@link BitmapBaseCommands#bitfield(String,
     * BitFieldSubCommands[])} and {@link BitmapBaseCommands#bitfieldReadOnly(String,
     * BitFieldReadOnlySubCommands[])}.
     *
     * @param subCommands commands that holds arguments to be included in the argument String array.
     * @return a String array that holds the sub commands and their arguments.
     */
    public static String[] createBitFieldArgs(BitFieldSubCommands[] subCommands) {
        String[] arguments = {};

        for (int i = 0; i < subCommands.length; i++) {
            arguments = concatenateArrays(arguments, subCommands[i].toArgs());
        }

        return arguments;
    }
}
