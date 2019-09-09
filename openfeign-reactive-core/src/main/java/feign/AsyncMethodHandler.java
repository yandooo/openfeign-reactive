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
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import io.github.robwin.circuitbreaker.CircuitBreaker;
import io.github.robwin.circuitbreaker.CircuitBreakerConfig;
import io.github.robwin.decorators.Decorators;
import io.github.robwin.retry.AsyncRetry;
import io.github.robwin.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static feign.AsyncUtils.executionTracerIfAny;
import static feign.AsyncUtils.fallbackIfAny;
import static feign.Util.checkNotNull;
import static feign.Util.ensureClosed;
import static java.lang.String.format;

@SuppressWarnings("PMD")
@Slf4j
class AsyncMethodHandler implements InvocationHandlerFactory.MethodHandler {
    private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;

    private final MethodMetadata metadata;
    private final Target<?> target;
    private final AsyncFeignHttpClient client;
    private final Retryer retryer;
    private final List<RequestInterceptor> requestInterceptors;
    private final Logger logger;
    private final Logger.Level logLevel;
    private final RequestTemplate.Factory buildTemplateFromArgs;
    private final Request.Options options;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;
    private final boolean decode404;
    private final CircuitBreakerConfig circuitBreakerConfig;
    private final RetryConfig retryConfig;
    private final CircuitBreaker circuitBreaker;
    private final AsyncRetry retry;
    private final FallbackMethodHandler fallback;
    private ScheduledExecutorService retryScheduler;

    private AsyncMethodHandler(Target<?> target, AsyncFeignHttpClient client, Retryer retryer,
                               List<RequestInterceptor> requestInterceptors, Logger logger, Logger.Level logLevel, FallbackMethodHandler fallback,
                               MethodMetadata metadata, RequestTemplate.Factory buildTemplateFromArgs, Request.Options options, Decoder decoder,
                               ErrorDecoder errorDecoder, boolean decode404, CircuitBreakerConfig circuitBreakerConfig, RetryConfig retryConfig) {
        this.target = checkNotNull(target, "target must be not null");
        this.client = checkNotNull(client, "client must be not null");
        this.retryer = checkNotNull(retryer, "retryer for %s must be not null", target);
        this.requestInterceptors = checkNotNull(requestInterceptors, "requestInterceptors for %s must be not null", target);
        this.logger = checkNotNull(logger, "logger for %s must be not null", target);
        this.logLevel = checkNotNull(logLevel, "logLevel for %s must be not null", target);
        this.fallback = fallback;
        this.metadata = checkNotNull(metadata, "metadata for %s must be not null", target);
        this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs, "metadata for %s must be not null", target);
        this.options = checkNotNull(options, "options for %s must be not null", target);
        this.errorDecoder = checkNotNull(errorDecoder, "errorDecoder for %s must be not null", target);
        this.decoder = checkNotNull(decoder, "decoder for %s must be not null", target);
        this.decode404 = decode404;
        this.circuitBreakerConfig = circuitBreakerConfig;
        this.circuitBreaker = circuitBreakerConfig != null ? CircuitBreaker.of(metadata.configKey(), circuitBreakerConfig)
                : null;
        this.retryConfig = retryConfig;
        this.retry = retryConfig != null ? AsyncRetry.of(metadata.configKey(), retryConfig) : null;
        if (retry != null)
            retryScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public CompletableFuture<?> invoke(final Object[] argv) {

        final RequestTemplate template = buildTemplateFromArgs.create(argv);
        final Request request = targetRequest(template);
        final CompletableFuture<Object> completableFuture = executeAndDecode(request);

        Supplier<? extends CompletionStage<?>> decorate = null;
        if (circuitBreaker != null || retry != null) {
            Decorators.DecorateCompletionStage<?> decorateCompletionStage = Decorators
                    .ofCompletionStage(() -> completableFuture);
            if (retry != null) {
                decorateCompletionStage.withRetry(retry, retryScheduler);
            }
            if (circuitBreaker != null) {
                decorateCompletionStage.withCircuitBreaker(circuitBreaker);
            }
            decorate = decorateCompletionStage.decorate();
        }

        return executionTracerIfAny(request,
                fallbackIfAny(fallback, decorate != null ? (CompletableFuture<Object>) decorate.get() : completableFuture, argv));
    }

    /**
     * Executes request from {@code template} with {@code this.client} and
     * decodes the response. Result or occurred error wrapped in returned Future.
     *
     * @param request parsed request
     * @return future with decoded result or occurred error
     */
    private CompletableFuture<Object> executeAndDecode(final Request request) {
        final CompletableFuture<Object> decodedResultFuture = new CompletableFuture();

        logRequest(request);

        final Instant start = Instant.now();

        client.execute(request, this.options).whenComplete((res, thr) -> {
            if (thr == null) {
                boolean shouldClose = true;

                final long elapsedTime = Duration.between(start, Instant.now()).toMillis();

                try {
                    Response response = res;
                    // TODO: check why this buffering is needed
                    if (logLevel != Logger.Level.NONE) {
                        response = logger.logAndRebufferResponse(metadata.configKey(), logLevel, response, elapsedTime);
                    }

                    if (Response.class == metadata.returnType()) {
                        if (response.body() == null) {
                            decodedResultFuture.complete(response);
                        } else if (response.body().length() == null || response.body().length() > MAX_RESPONSE_BUFFER_SIZE) {
                            shouldClose = false;
                            decodedResultFuture.complete(response);
                        } else {
                            final byte[] bodyData = Util.toByteArray(response.body().asInputStream());
                            decodedResultFuture
                                    .complete(Response.builder().request(request)
                                            .status(response.status())
                                            .reason(response.reason())
                                            .headers(response.headers()).body(bodyData).build());
                        }
                    } else if (response.status() >= 200 && response.status() < 300) {
                        if (Void.class == metadata.returnType()) {
                            decodedResultFuture.complete(null);
                        } else {
                            decodedResultFuture.complete(decode(response));
                        }
                    } else if (decode404 && response.status() == 404) {
                        decodedResultFuture.complete(decoder.decode(response, metadata.returnType()));
                    } else {
                        decodedResultFuture.completeExceptionally(errorDecoder.decode(metadata.configKey(), response));
                    }
                } catch (Exception ex) {
                    decodedResultFuture.completeExceptionally(
                            new FeignException(500, format("%s reading %s %s", ex.getMessage(), request.httpMethod(), request.url()), ex));
                } finally {
                    if (shouldClose) {
                        ensureClosed(res.body());
                    }
                }
            } else {
                decodedResultFuture.completeExceptionally(thr);
            }
        });

        return decodedResultFuture;
    }

    /**
     * Associates request to defined target.
     *
     * @param template request template
     * @return fully formed request
     */
    private Request targetRequest(final RequestTemplate template) {
        for (RequestInterceptor interceptor : requestInterceptors) {
            interceptor.apply(template);
        }
        return target.apply(template);
    }

    /**
     * Transforms HTTP response body into object using decoder.
     *
     * @param response HTTP response
     * @return decoded result
     * @throws IOException     IO exception during the reading of InputStream of
     *                         response
     * @throws DecodeException when decoding failed due to a checked or unchecked
     *                         exception besides IOException
     * @throws FeignException  when decoding succeeds, but conveys the operation
     *                         failed
     */
    private Object decode(final Response response) throws IOException, FeignException {
        try {
            return decoder.decode(response, metadata.returnType());
        } catch (FeignException feignException) {
            throw feignException;
        } catch (RuntimeException unexpectedException) {
            throw new DecodeException(response != null ? response.status() : 500, unexpectedException.getMessage(), unexpectedException);
        }
    }

    /**
     * Logs request.
     *
     * @param request HTTP request
     */
    private void logRequest(final Request request) {
        if (logLevel != Logger.Level.NONE) {
            logger.logRequest(metadata.configKey(), logLevel, request);
        }
    }

    static class Factory {
        private final AsyncFeignHttpClient client;
        private final Retryer retryer;
        private final List<RequestInterceptor> requestInterceptors;
        private final Logger logger;
        private final Logger.Level logLevel;
        private final boolean decode404;
        private final CircuitBreakerConfig circuitBreakerConfig;

        Factory(final AsyncFeignHttpClient client, final Retryer retryer, final List<RequestInterceptor> requestInterceptors,
                final Logger logger, final Logger.Level logLevel, final boolean decode404,
                CircuitBreakerConfig circuitBreakerConfig) {
            this.client = checkNotNull(client, "client must not be null");
            this.retryer = checkNotNull(retryer, "retryer must not be null");
            this.requestInterceptors = checkNotNull(requestInterceptors, "requestInterceptors must not be null");
            this.logger = checkNotNull(logger, "logger must not be null");
            this.logLevel = checkNotNull(logLevel, "logLevel must not be null");
            this.decode404 = decode404;
            this.circuitBreakerConfig = circuitBreakerConfig;
        }

        MethodHandler create(final Target<?> target, final FallbackMethodHandler fallback, final MethodMetadata metadata,
                             final RequestTemplate.Factory buildTemplateFromArgs, final Request.Options options, final Decoder decoder,
                             final ErrorDecoder errorDecoder, final CircuitBreakerConfig circuitBreakerConfig, final RetryConfig retryConfig) {
            return new AsyncMethodHandler(target, client, retryer, requestInterceptors, logger, logLevel, fallback, metadata,
                    buildTemplateFromArgs, options, decoder, errorDecoder, decode404, circuitBreakerConfig, retryConfig);
        }
    }
}
