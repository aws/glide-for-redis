/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import response.ResponseOuterClass.RequestError;
import response.ResponseOuterClass.Response;

/** Holder for resources required to dispatch responses and used by {@link ReadHandler}. */
@RequiredArgsConstructor
public class CallbackDispatcher {

    /** A message handler instance. */
    protected final MessageHandler messageHandler;

    /** Unique request ID (callback ID). Thread-safe and overflow-safe. */
    protected final AtomicInteger nextAvailableRequestId = new AtomicInteger(0);

    /**
     * Storage of Futures to handle responses. Map key is callback id, which starts from 0. The value
     * is a CompletableFuture that is returned to the user and completed when the request is done.
     *
     * <p>Note: Protobuf packet contains callback ID as uint32, but it stores data as a bit field.
     * Negative Java values would be shown as positive on Rust side. There is no data loss, because
     * callback ID remains unique.
     */
    protected final ConcurrentHashMap<Integer, CompletableFuture<Response>> responses =
            new ConcurrentHashMap<>();

    /**
     * Storage of freed callback IDs. It is needed to avoid occupying an ID being used and to speed up
     * search for a next free ID.
     */
    // TODO: Optimize to avoid growing up to 2e32 (16 Gb)
    // https://github.com/aws/glide-for-redis/issues/704
    protected final ConcurrentLinkedQueue<Integer> freeRequestIds = new ConcurrentLinkedQueue<>();

    /**
     * Register a new request to be sent. Once response received, the given future completes with it.
     *
     * @return A pair of unique callback ID which should set into request and a client promise for
     *     response.
     */
    public Pair<Integer, CompletableFuture<Response>> registerRequest() {
        var future = new CompletableFuture<Response>();
        Integer callbackId = freeRequestIds.poll();
        if (callbackId == null) {
            // on null, we have no available request ids available in freeRequestIds
            // instead, get the next available request from counter
            callbackId = nextAvailableRequestId.getAndIncrement();
        }
        responses.put(callbackId, future);
        return Pair.of(callbackId, future);
    }

    public CompletableFuture<Response> registerConnection() {
        return registerRequest().getValue();
    }

    /**
     * Complete the corresponding client promise, handle error and free resources.
     *
     * @param response A response received
     */
    public void completeRequest(Response response) {
        if (response.hasClosingError()) {
            // According to https://github.com/aws/glide-for-redis/issues/851
            // a response with a closing error may arrive with any/random callback ID (usually -1)
            // CommandManager and ConnectionManager would close the UDS channel on ClosingException
            distributeClosingException(response.getClosingError());
            return;
        }
        // pass pushes to the message handler and stop processing them
        if (response.getIsPush()) {
            messageHandler.handle(response);
            return;
        }
        // Complete and return the response at callbackId
        // free up the callback ID in the freeRequestIds list
        int callbackId = response.getCallbackIdx();
        CompletableFuture<Response> future = responses.remove(callbackId);
        if (future != null) {
            freeRequestIds.add(callbackId);
            if (response.hasRequestError()) {
                RequestError error = response.getRequestError();
                String msg = error.getMessage();
                switch (error.getType()) {
                    case Unspecified:
                        // Unspecified error on Redis service-side
                        future.completeExceptionally(new RequestException(msg));
                    case ExecAbort:
                        // Transactional error on Redis service-side
                        future.completeExceptionally(new ExecAbortException(msg));
                    case Timeout:
                        // Timeout from Glide to Redis service
                        future.completeExceptionally(new TimeoutException(msg));
                    case Disconnect:
                        // Connection problem between Glide and Redis
                        future.completeExceptionally(new ConnectionException(msg));
                    default:
                        // Request or command error from Redis
                        future.completeExceptionally(new RequestException(msg));
                }
            }
            future.completeAsync(() -> response);
        } else {
            // TODO: log an error thru logger.
            // probably a response was received after shutdown or `registerRequest` call was missing
            System.err.printf(
                    "Received a response for not registered callback id %d, request error = %s%n",
                    callbackId, response.getRequestError());
            distributeClosingException("Client is in an erroneous state and should close");
        }
    }

    /**
     * Distribute {@link ClosingException} to all pending requests. {@link CommandManager} and {@link
     * ConnectionManager} should catch it, handle and close the UDS connection.<br>
     * Should be used to termination the client/connection only.
     *
     * @param message Exception message
     */
    public void distributeClosingException(String message) {
        responses.values().forEach(f -> f.completeExceptionally(new ClosingException(message)));
        responses.clear();
    }

    public void shutdownGracefully() {
        String msg = "Operation terminated: The closing process has been initiated for the resource.";
        responses.values().forEach(future -> future.completeExceptionally(new ClosingException(msg)));
        responses.clear();
    }
}
