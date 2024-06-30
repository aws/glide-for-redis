/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

public interface ClusterScanCursor extends AutoCloseable {
    String getCursor();

    static ClusterScanCursor initialCursor() {
        return new InitialCursor();
    }

    final class InitialCursor implements ClusterScanCursor {

        private InitialCursor() {}

        @Override
        public String getCursor() {
            return "0";
        }

        @Override
        public void close() throws Exception {}
    }
}
