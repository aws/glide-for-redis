/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

public interface ClusterScanCursor {
    String getCursorHandle();
    boolean isFinished();
    void releaseCursorHandle();

    ClusterScanCursor INITIAL_CURSOR_INSTANCE = new ClusterScanCursor() {
        @Override
        public String getCursorHandle() {
            return null;
        }

        @Override
        public boolean isFinished() {
            // The initial cursor can always request more data.
            return false;
        }

        @Override
        public void releaseCursorHandle() {
            // Ignore.
        }
    };

    static ClusterScanCursor initalCursor() {
        return INITIAL_CURSOR_INSTANCE;
    }
}
