/*
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2019 Léo Montana and Contributors
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *   documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *   rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 *   persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 *   BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 *   DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *   FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package feign;

import feign.InvocationHandlerFactory.MethodHandler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static feign.Util.checkNotNull;

/**
 * {@link InvocationHandler} implementation that transforms calls to methods of
 * feign contract into asynchronous HTTP requests via AsyncFeignHttpClient.
 */
final class AsyncInvocationHandler implements InvocationHandler {

    private final Target<?> target;
    private final Map<Method, MethodHandler> dispatch;

    private AsyncInvocationHandler(final Target<?> target, final Map<Method, MethodHandler> dispatch) {
        this.target = checkNotNull(target, "target must not be null");
        this.dispatch = checkNotNull(dispatch, "dispatch must not be null");
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        switch (method.getName()) {
            case "equals":
                final Object otherHandler = args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
                return equals(otherHandler);
            case "hashCode":
                return hashCode();
            case "toString":
                return toString();
            default:
                return invokeRequestMethod(method, args);
        }
    }

    /**
     * Transforms method invocation into request that executed by
     * {@link AsyncFeignHttpClient}.
     *
     * @param method invoked method
     * @param args   provided arguments to method
     * @return future with decoded result or occurred exception
     */
    private Object invokeRequestMethod(final Method method, final Object[] args) {
        try {
            return dispatch.get(method).invoke(args);
        } catch (Throwable throwable) {
            if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                final CompletableFuture<?> completableFuture = new CompletableFuture<>();
                completableFuture.completeExceptionally(throwable);
                return completableFuture;
            }
            throw new FeignException(500, "Error invoking method", throwable);
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof AsyncInvocationHandler) {
            final AsyncInvocationHandler otherHandler = (AsyncInvocationHandler) other;
            return this.target.equals(otherHandler.target);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }

    @Override
    public String toString() {
        return target.toString();
    }

    static final class Factory implements InvocationHandlerFactory {

        @Override
        public InvocationHandler create(final Target target, final Map<Method, MethodHandler> dispatch) {
            return new AsyncInvocationHandler(target, dispatch);
        }
    }
}
