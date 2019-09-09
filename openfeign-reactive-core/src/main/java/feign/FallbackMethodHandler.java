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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static feign.Util.checkNotNull;

@Getter
@Slf4j
class FallbackMethodHandler {

    private final Method fallbackMethod;
    private final Fallback fallback;
    private final Set<Class<? extends Throwable>> ignoreExceptions = new HashSet<>();
    private Predicate<? extends Throwable> ignorePredicate;
    private DefaultMethodHandler defaultMethodHandler;

    public FallbackMethodHandler(Method fallbackMethod, Fallback fallback) {
        checkNotNull(fallbackMethod, "fallbackMethod must be not null");
        checkNotNull(fallback, "fallback must be not null");

        this.fallbackMethod = fallbackMethod;
        this.fallback = fallback;
        if (fallback.ignoreExceptions() != null && fallback.ignoreExceptions().length > 0) {
            ignoreExceptions.addAll(Arrays.asList(fallback.ignoreExceptions()));
        }
        if (fallback.ignorePredicate() != null) {
            try {
                ignorePredicate = fallback.ignorePredicate().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Can't instantiate ignore function [" + fallback.ignorePredicate() + "] for fallback [" + fallback + "]",
                        e);
            }
        }
    }

    public void setDefaultMethodHandler(final DefaultMethodHandler defaultMethodHandler) {
        checkNotNull(defaultMethodHandler, "defaultMethodHandler for %s must be not null", defaultMethodHandler);
        this.defaultMethodHandler = defaultMethodHandler;
    }

    public boolean isIgnorable(final Throwable throwable) {
        if (ignoreExceptions == null) {
            return false;
        }
        for (Class<? extends Throwable> ignoreException : ignoreExceptions) {
            if (ignoreException.isAssignableFrom(throwable.getClass())) {
                return true;
            }
        }
        return false;
    }

    public boolean isIgnorablePredicate(final Throwable throwable) {
        try {
            return ignorePredicate == null || ((Predicate<Throwable>) ignorePredicate).test(throwable);
        } catch (Exception e) {
            log.error("Exception evaluating fallback ignore exception function. Exception is suppressed", e);
        }
        return false;
    }

    public Object invoke(final Object[] argv, final Throwable throwable) throws Throwable {
        final Object[] localArgs = argv == null ? new Object[1] : Arrays.copyOf(argv, argv.length + 1);
        localArgs[localArgs.length - 1] = throwable;

        return defaultMethodHandler.invoke(localArgs);
    }

    @Override
    public String toString() {
        return "FallbackMethodHandler{" + "fallback=" + fallback + '}';
    }
}
