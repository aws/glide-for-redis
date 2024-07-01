/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.GenericCommands;
import java.util.ArrayList;
import java.util.List;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments to {@link GenericCommands#sort(String, SortOptions)}, {@link
 * GenericCommands#sortReadOnly(String, SortOptions)}, and {@link GenericCommands#sortStore(String,
 * String, SortOptions)}
 *
 * @see <a href="https://redis.io/commands/sort/">redis.io</a> and <a
 *     href="https://redis.io/docs/latest/commands/sort_ro/">redis.io</a>
 */
@SuperBuilder
public class SortOptions extends SortBaseOptions {
    /**
     * <code>BY</code> subcommand string to include in the <code>SORT</code> and <code>SORT_RO</code>
     * commands.
     */
    public static final String BY_COMMAND_STRING = "BY";

    /**
     * <code>GET</code> subcommand string to include in the <code>SORT</code> and <code>SORT_RO</code>
     * commands.
     */
    public static final String GET_COMMAND_STRING = "GET";

    /**
     * A pattern to sort by external keys instead of by the elements stored at the key themselves. The
     * pattern should contain an asterisk (*) as a placeholder for the element values, where the value
     * from the key replaces the asterisk to create the key name. For example, if <code>key</code>
     * contains IDs of objects, <code>byPattern</code> can be used to sort these IDs based on an
     * attribute of the objects, like their weights or timestamps.
     */
    private final String byPattern;

    /**
     * A pattern used to retrieve external keys' values, instead of the elements at <code>key</code>.
     * The pattern should contain an asterisk (*) as a placeholder for the element values, where the
     * value from <code>key</code> replaces the asterisk to create the <code>key</code> name. This
     * allows the sorted elements to be transformed based on the related keys values. For example, if
     * <code>key</code> contains IDs of users, <code>getPatterns</code> can be used to retrieve
     * specific attributes of these users, such as their names or email addresses. E.g., if <code>
     * getPatterns</code> is <code>name_*</code>, the command will return the values of the keys
     * <code>name_&lt;element&gt;</code> for each sorted element. Multiple <code>getPatterns</code>
     * arguments can be provided to retrieve multiple attributes. The special value <code>#</code> can
     * be used to include the actual element from <code>key</code> being sorted. If not provided, only
     * the sorted elements themselves are returned.<br>
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for more information.
     */
    @Singular private final List<String> getPatterns;

    /**
     * Creates the arguments to be used in <code>SORT</code> and <code>SORT_RO</code> commands.
     *
     * @return a String array that holds the sub commands and their arguments.
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>(List.of(super.toArgs()));

        if (byPattern != null) {
            optionArgs.addAll(List.of(BY_COMMAND_STRING, byPattern));
        }

        if (getPatterns != null) {
            getPatterns.stream()
                    .forEach(getPattern -> optionArgs.addAll(List.of(GET_COMMAND_STRING, getPattern)));
        }

        return optionArgs.toArray(new String[0]);
    }
}
