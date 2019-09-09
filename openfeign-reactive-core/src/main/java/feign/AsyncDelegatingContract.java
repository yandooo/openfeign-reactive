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

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static feign.Util.*;

public class AsyncDelegatingContract implements Contract {

    private final Contract delegate;

    public AsyncDelegatingContract(final Contract delegate) {
        this.delegate = checkNotNull(delegate, "delegate must not be null");
    }

    @Override
    public List<MethodMetadata> parseAndValidatateMetadata(final Class<?> targetType) {
        final List<MethodMetadata> metadatas = this.delegate.parseAndValidatateMetadata(targetType);

        for (final MethodMetadata metadata : metadatas) {
            final Type type = metadata.returnType();

            if (type instanceof ParameterizedType && ((ParameterizedType) type).getRawType().equals(CompletableFuture.class)) {
                final Type actualType = resolveLastTypeParameter(type, CompletableFuture.class);
                metadata.returnType(actualType);
            } else {
                throw new IllegalStateException(
                        String.format("Method %s of contract %s doesn't return java.util.concurrent.CompletableFuture",
                                metadata.configKey(), targetType.getSimpleName()));
            }
        }

        return metadatas;
    }

    public Map<String, FallbackMethodHandler> getFallbacks(final Class<?> cls) {
        final Map<String, FallbackMethodHandler> fallbacks = new HashMap<>();
        final Map<String, Method> methodsCache = Arrays.stream(cls.getMethods())
                .collect(Collectors.toMap(Method::getName, Function.identity()));

        for (final Method srcMethod : methodsCache.values()) {
            final Fallback fallback = srcMethod.getDeclaredAnnotation(Fallback.class);
            if (fallback != null) {
                final String methodName = fallback.value();
                final Method fallbackMethod = methodsCache.get(methodName);

                if (fallbackMethod == null || !isDefault(fallbackMethod)
                        || !fallbackMethod.getReturnType().equals(CompletableFuture.class)
                        || lastArgumentIsNotThrowable(fallbackMethod)) {
                    throw new IllegalStateException(
                            String.format("Method %s of contract %s doesn't have right fallback method", srcMethod, cls));
                }
                fallbacks.put(Feign.configKey(cls, srcMethod), new FallbackMethodHandler(fallbackMethod, fallback));
            }
        }

        return fallbacks;
    }

    private boolean lastArgumentIsNotThrowable(final Method method) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        return parameterTypes.length == 0 || !Throwable.class.isAssignableFrom(parameterTypes[parameterTypes.length - 1]);
    }

}
