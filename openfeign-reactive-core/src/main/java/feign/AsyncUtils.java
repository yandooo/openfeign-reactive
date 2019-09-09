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

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class AsyncUtils {

    private static final long LOG_WARN_THRESHOLD_MS = 2000;
    private static final long LOG_ERROR_THRESHOLD_MS = 4000;

    private static final String LOG_MSG_TEMPLATE = "Slow network call execution detected: method=[%s], url=[%s], timeMs=[%d], timeSec=[%.2f], exception=[%b]";

    public static <T> CompletableFuture<T> fallbackIfAny(final FallbackMethodHandler fallback,
                                                         final CompletableFuture<T> executionStage, final Object[] argv) {

        if (fallback == null) {
            return executionStage;
        }

        final CompletableFuture<T> fallbackPromise = new CompletableFuture<>();
        executionStage.whenComplete((o, throwable) -> {
            if (throwable == null) {
                fallbackPromise.complete(o);
            }

            if (fallback == null || fallback.isIgnorable(throwable) || fallback.isIgnorablePredicate(throwable)) {
                fallbackPromise.completeExceptionally(throwable);
            } else {
                try {
                    log.warn("Attempt to execute fallback [{}]", fallback);
                    final CompletableFuture<T> fallbackExecution = (CompletableFuture<T>) fallback.invoke(argv, throwable);
                    fallbackExecution.whenComplete((t, fth) -> {
                        if (fth == null) {
                            log.debug("Fallback executed successfully [{}], result [{}]", fallback, t);
                            fallbackPromise.complete(t);
                        } else {
                            log.warn("Fallback completed exceptionally [{}], exception [{}]", fallback, fth);
                            fallbackPromise.completeExceptionally(fth);
                        }
                    });
                } catch (Throwable ex) {
                    log.warn("Exception invoking fallback method [{}], exception [{}]", fallback, ex);
                    fallbackPromise.completeExceptionally(ex);
                }
            }
        });

        return fallbackPromise;
    }

    public static <T> FeignCompletableFuture<T> executionTracerIfAny(final Request request,
                                                                     final CompletableFuture<T> executionStage) {
        final FeignContext context = getFeignContext(executionStage);
        final FeignCompletableFuture<T> tracingPromise = new FeignCompletableFuture<>(context);

        long p1Millis = System.currentTimeMillis();
        executionStage.whenComplete((o, throwable) -> {
            long p2Millis = System.currentTimeMillis();
            long totalTime = p2Millis - p1Millis;

            context.setRequest(request).setExecutionMillis(totalTime);
            if (totalTime >= LOG_WARN_THRESHOLD_MS) {
                final String frmMsgs = String.format(LOG_MSG_TEMPLATE, request.httpMethod(), request.url(),
                        totalTime, totalTime / 1000.0, throwable);

                if (totalTime < LOG_ERROR_THRESHOLD_MS)
                    log.warn(frmMsgs);
                else
                    log.error(frmMsgs);
            }

            if (throwable == null)
                tracingPromise.complete(o);
            else
                tracingPromise.completeExceptionally(throwable);
        });

        return tracingPromise;
    }

    private static FeignContext getFeignContext(final CompletableFuture<?> executionStage) {
        return executionStage != null && executionStage instanceof FeignCompletableFuture
                ? ((FeignCompletableFuture<?>) executionStage).getFeignContext() : new FeignContext();
    }

}
