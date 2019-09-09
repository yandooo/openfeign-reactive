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
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import io.github.robwin.circuitbreaker.CircuitBreakerConfig;
import io.github.robwin.retry.RetryConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

import static feign.Util.checkNotNull;
import static feign.Util.isDefault;

@Slf4j
public class AsyncFeign extends Feign {
    private final ParseHandlersByName targetToHandlersByName;
    private final InvocationHandlerFactory factory;

    private AsyncFeign(final ParseHandlersByName targetToHandlersByName, final InvocationHandlerFactory factory) {
        this.targetToHandlersByName = targetToHandlersByName;
        this.factory = factory;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T newInstance(Target<T> target) {
        final HandlersDescriptor handlersDescriptor = targetToHandlersByName.apply(target);

        final Map<String, MethodHandler> nameToHandler = handlersDescriptor.getConfigKeyToMethodHandlerMap();
        final Map<Method, MethodHandler> methodToHandler = new LinkedHashMap<>();
        final List<DefaultMethodHandler> defaultMethodHandlers = new LinkedList<>();

        for (final Method method : target.type().getMethods()) {
            if (isDefault(method)) {
                final DefaultMethodHandler handler = new DefaultMethodHandler(method);
                defaultMethodHandlers.add(handler);
                methodToHandler.put(method, handler);

                final FallbackMethodHandler fmh = getFallbackMethod(handlersDescriptor, method);
                if (fmh != null) {
                    fmh.setDefaultMethodHandler(handler);
                }
            } else {
                methodToHandler.put(method, nameToHandler.get(Feign.configKey(target.type(), method)));
            }
        }

        final InvocationHandler handler = factory.create(target, methodToHandler);
        T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(), new Class<?>[]{target.type()}, handler);

        for (final DefaultMethodHandler defaultMethodHandler : defaultMethodHandlers) {
            defaultMethodHandler.bindTo(proxy);
        }

        return proxy;
    }

    public FallbackMethodHandler getFallbackMethod(final HandlersDescriptor handlersDescriptor, final Method method) {
        return handlersDescriptor.originalToFallbackMethodMap.values().stream()
                .filter(fm -> fm.getFallbackMethod().equals(method)).findFirst().orElse(null);
    }

    @SuppressWarnings("PMD")
    public static final class Builder extends Feign.Builder {
        private final List<RequestInterceptor> requestInterceptors = new ArrayList<>();
        private AsyncFeignHttpClient asyncFeignHttpClient;
        private Logger.Level logLevel = Logger.Level.NONE;
        private Contract contract = new AsyncDelegatingContract(new Contract.Default());
        private AsyncFeignHttpClient client;
        private Retryer retryer = new Retryer.Default();
        private CircuitBreakerConfig circuitBreakerConfig;
        private RetryConfig retryConfig;
        private Logger logger = new Logger.NoOpLogger();
        private Encoder encoder = new Encoder.Default();
        private Decoder decoder = new Decoder.Default();
        private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
        private Request.Options options = new Request.Options();
        private InvocationHandlerFactory invocationHandlerFactory = new AsyncInvocationHandler.Factory();
        private boolean decode404;

        /**
         * Unsupported operation.
         */
        @Override
        public Builder client(final Client client) {
            throw new UnsupportedOperationException(
                    "Use `asyncRestTemplate()` to set appropriate asynchronous client instance");
        }

        /**
         * Unsupported operation.
         */
        @Override
        public Builder invocationHandlerFactory(final InvocationHandlerFactory invocationHandlerFactory) {
            throw new UnsupportedOperationException(
                    "Existing factory interfaces are all synchronous. Therefore there is intentionally hardcoded implementation.");
        }

        /**
         * Sets a Spring AsyncFeignHttpClient instance to use to make the client.
         *
         * @param asyncFeignHttpClient instance
         * @return this builder
         */
        public Builder asyncHttpClient(final AsyncFeignHttpClient asyncFeignHttpClient) {
            this.asyncFeignHttpClient = asyncFeignHttpClient;
            return this;
        }

        /**
         * Sets log level.
         *
         * @param logLevel log level
         * @return this builder
         */
        @Override
        public Builder logLevel(final Logger.Level logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        /**
         * Sets contract. Provided contract will be wrapped in
         * {@link AsyncDelegatingContract}
         *
         * @param contract contract.
         * @return this builder
         */
        @Override
        public Builder contract(final Contract contract) {
            this.contract = new AsyncDelegatingContract(contract);
            return this;
        }

        /**
         * Sets logger.
         *
         * @param logger logger
         * @return this builder
         */
        @Override
        public Builder logger(final Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets encoder.
         *
         * @param encoder encoder
         * @return this builder
         */
        @Override
        public Builder encoder(final Encoder encoder) {
            this.encoder = encoder;
            return this;
        }

        /**
         * Sets decoder.
         *
         * @param decoder decoder
         * @return this builder
         */
        @Override
        public Builder decoder(final Decoder decoder) {
            this.decoder = decoder;
            return this;
        }

        /**
         * This flag indicates that the {@link #decoder(Decoder) decoder} should
         * process responses with 404 status, specifically returning null or empty
         * instead of throwing {@link FeignException}.
         *
         * <p>
         * All first-party (ex gson) decoders return well-known empty values
         * defined by {@link Util#emptyValueOf}. To customize further, wrap an
         * existing {@link #decoder(Decoder) decoder} or make your own.
         *
         * <p>
         * This flag only works with 404, as opposed to all or arbitrary status
         * codes. This was an explicit decision: 404 - empty is safe, common and
         * doesn't complicate redirection, retry or fallback policy.
         *
         * @return this builder
         */
        @Override
        public Builder decode404() {
            this.decode404 = true;
            return this;
        }

        /**
         * Sets error decoder.
         *
         * @param errorDecoder error decoder
         * @return this builder
         */
        @Override
        public Builder errorDecoder(final ErrorDecoder errorDecoder) {
            this.errorDecoder = errorDecoder;
            return this;
        }

        /**
         * Sets request options.
         *
         * @param options HTTP request options.
         * @return this builder
         */
        @Override
        public Builder options(final Request.Options options) {
            this.options = options;
            return this;
        }

        /**
         * Adds a single request interceptor to the builder.
         *
         * @param requestInterceptor request interceptor to add
         * @return this builder
         */
        @Override
        public Builder requestInterceptor(final RequestInterceptor requestInterceptor) {
            this.requestInterceptors.add(requestInterceptor);
            return this;
        }

        /**
         * Sets the full set of request interceptors for the builder, overwriting
         * any previous interceptors.
         *
         * @param requestInterceptors set of request interceptors
         * @return this builder
         */
        @Override
        public Builder requestInterceptors(final Iterable<RequestInterceptor> requestInterceptors) {
            this.requestInterceptors.clear();
            for (RequestInterceptor requestInterceptor : requestInterceptors) {
                this.requestInterceptors.add(requestInterceptor);
            }
            return this;
        }

        /**
         * Defines target and builds client.
         *
         * @param apiType API interface
         * @param url     base URL
         * @param <T>     class of API interface
         * @return built client
         */
        @Override
        public <T> T target(final Class<T> apiType, final String url) {
            return target(new Target.HardCodedTarget<>(apiType, url));
        }

        /**
         * Defines target and builds client.
         *
         * @param target target instance
         * @param <T>    class of API interface
         * @return built client
         */
        @Override
        public <T> T target(final Target<T> target) {
            return build().newInstance(target);
        }

        public Builder circuitBreakerConfig(final CircuitBreakerConfig circuitBreakerConfig) {
            this.circuitBreakerConfig = circuitBreakerConfig;
            return this;
        }

        public Builder retryConfig(final RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        @Override
        public AsyncFeign build() {
            checkNotNull(this.asyncFeignHttpClient, "AsyncFeignHttpClient instance wasn't provided in AsyncFeign builder");

            final AsyncMethodHandler.Factory methodHandlerFactory = new AsyncMethodHandler.Factory(asyncFeignHttpClient, retryer,
                    requestInterceptors, logger, logLevel, decode404, circuitBreakerConfig);
            final ParseHandlersByName handlersByName = new ParseHandlersByName(contract, options, encoder, decoder,
                    errorDecoder, circuitBreakerConfig, retryConfig, methodHandlerFactory);
            return new AsyncFeign(handlersByName, invocationHandlerFactory);
        }
    }

    private static final class ParseHandlersByName {
        private final Contract contract;
        private final Request.Options options;
        private final Encoder encoder;
        private final Decoder decoder;
        private final ErrorDecoder errorDecoder;
        private final CircuitBreakerConfig circuitBreakerConfig;
        private final RetryConfig retryConfig;
        private final AsyncMethodHandler.Factory factory;

        ParseHandlersByName(final Contract contract, final Request.Options options, final Encoder encoder,
                            final Decoder decoder, final ErrorDecoder errorDecoder, final CircuitBreakerConfig circuitBreakerConfig,
                            final RetryConfig retryConfig, final AsyncMethodHandler.Factory factory) {
            this.contract = contract;
            this.options = options;
            this.factory = factory;
            this.errorDecoder = errorDecoder;
            this.encoder = checkNotNull(encoder, "encoder must not be null");
            this.decoder = checkNotNull(decoder, "decoder must not be null");
            this.circuitBreakerConfig = circuitBreakerConfig;
            this.retryConfig = retryConfig;
        }

        HandlersDescriptor apply(final Target key) {
            final List<MethodMetadata> metadata = contract.parseAndValidatateMetadata(key.type());
            final Map<String, FallbackMethodHandler> originalToFallbackMap = ((AsyncDelegatingContract) contract)
                    .getFallbacks(key.type());
            final Map<String, MethodHandler> configKeyToMethodHandlerMap = new LinkedHashMap<>();

            for (final MethodMetadata md : metadata) {
                BuildTemplateByResolvingArgs buildTemplate;

                if (!md.formParams().isEmpty() && md.template().bodyTemplate() == null) {
                    buildTemplate = new BuildTemplateByResolvingArgs.BuildFormEncodedTemplateFromArgs(md, encoder);
                } else if (md.bodyIndex() != null) {
                    buildTemplate = new BuildTemplateByResolvingArgs.BuildEncodedTemplateFromArgs(md, encoder);
                } else {
                    buildTemplate = new BuildTemplateByResolvingArgs(md);
                }

                final FallbackMethodHandler fallback = originalToFallbackMap.get(md.configKey());
                log.info("Detected method [{}] fallback [{}]. if [null] there is no fallback defined for the method",
                        md.configKey(), fallback);

                configKeyToMethodHandlerMap.put(md.configKey(), factory.create(key, fallback, md, buildTemplate, options,
                        decoder, errorDecoder, circuitBreakerConfig, retryConfig));
            }

            return HandlersDescriptor.builder().originalToFallbackMethodMap(originalToFallbackMap)
                    .configKeyToMethodHandlerMap(configKeyToMethodHandlerMap).build();
        }
    }

    @lombok.Builder
    @Getter
    public static final class HandlersDescriptor {
        private Map<String, MethodHandler> configKeyToMethodHandlerMap;
        private Map<String, FallbackMethodHandler> originalToFallbackMethodMap;
    }
}
