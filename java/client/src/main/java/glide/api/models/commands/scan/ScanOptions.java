/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import lombok.experimental.SuperBuilder;

@SuperBuilder
public class ScanOptions extends BaseScanOptions {
    public enum Type {
        ;
    }

    private Type type;

    @Override
    public String[] toArgs() {
        return super.toArgs();
    }

    // Add function to populate a ClusterScan request
}
