/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** A Redis client with async capabilities */
public interface AsyncClient<T> extends Client {

    long DEFAULT_TIMEOUT_MILLISECOND = 1000;

    Future<T> asyncSet(String key, String value);

    Future<String> asyncGet(String key);

    default <T> T waitForResult(Future<T> future) {
        return waitForResult(future, DEFAULT_TIMEOUT_MILLISECOND);
    }

    default <T> T waitForResult(Future<T> future, long timeout) {
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("A task timed out", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Client error", e);
        } catch (InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) {
                // restore interrupt
                Thread.interrupted();
            }
            throw new RuntimeException("The thread was interrupted", e);
        }
    }
}
