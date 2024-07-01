/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

public enum ResponseFlags {
    /** Strings in the response are UTF-8 encoded */
    ENCODING_UTF8,
    /** Null is a valid response */
    IS_NULLABLE,
}
