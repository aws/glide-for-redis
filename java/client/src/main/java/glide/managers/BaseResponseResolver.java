/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static glide.api.BaseClient.OK;

import glide.api.models.exceptions.RedisException;
import lombok.AllArgsConstructor;
import response.ResponseOuterClass.Response;

/**
 * Response resolver responsible for evaluating the Redis response object with a success or failure.
 */
@AllArgsConstructor
public class BaseResponseResolver implements RedisExceptionCheckedFunction<Response, Object> {

    private RedisExceptionCheckedFunction<Long, Object> respPointerResolver;

    /**
     * Extracts value from the RESP pointer.
     *
     * @return A generic Object with the Response or null if the response is empty
     */
    public Object apply(Response response) throws RedisException {
        // Note: errors are already handled before in CallbackDispatcher
        assert !response.hasClosingError() : "Unhandled response closing error";
        assert !response.hasRequestError() : "Unhandled response request error";

        if (response.hasConstantResponse()) {
            return OK;
        }
        if (response.hasRespPointer()) {
            // Return the shared value - which may be a null value
            return respPointerResolver.apply(response.getRespPointer());
        }
        // if no response payload is provided, assume null
        return null;
    }
}
