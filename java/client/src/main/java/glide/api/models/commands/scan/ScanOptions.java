/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import glide.api.commands.GenericCommands;
import glide.api.commands.GenericClusterCommands;
import glide.utils.ArrayTransformUtils;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link GenericCommands#scan} and {@link GenericClusterCommands#scan}.
 *
 * @see <a href="https://valkey.io/commands/scan/">valkey.io</a>
 */
@SuperBuilder
public class ScanOptions extends BaseScanOptions {
    /** <code>TYPE</code> option string to include in the <code>SCAN</code> commands. */
    public static final String TYPE_OPTION_STRING = "TYPE";

    /**
     * Use this option to ask SCAN to only return objects that match a given type. <br>
     * The filter is applied after elements are retrieved from the database, so the option does not
     * reduce the amount of work the server has to do to complete a full iteration. For rare types
     * you may receive no elements in many iterations.
     */
    private final ObjectType type;

    public enum ObjectType {
        STRING,
        LIST,
        SET,
        ZSET,
        HASH,
        STREAM;
    }

    @Override
    public String[] toArgs() {
        if (type != null) {
            return ArrayTransformUtils.concatenateArrays(super.toArgs(), new String[]{TYPE_OPTION_STRING, type.toString()});
        }
        return super.toArgs();
    }
}
