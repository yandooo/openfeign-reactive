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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.google.common.util.concurrent.Uninterruptibles;
import feign.api.IceCreamService;
import feign.form.FormEncoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import io.github.robwin.circuitbreaker.CircuitBreakerConfig;
import io.github.robwin.circuitbreaker.CircuitBreakerOpenException;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@Slf4j
public class ClientSideTimeoutTest extends AbstractTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options()
            .port(8089)
            .asynchronousResponseEnabled(true).
                    jettyAcceptors(8).containerThreads(50));

    @Test
    public void readTimeoutFor10MsTest() {

        boolean caughtTimeout = false;
        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/authorization"))
                .willReturn(aResponse().withFixedDelay(1000)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getHttpClientWithTimeout(10))
                .encoder(new FormEncoder(new JacksonEncoder(TestUtils.MAPPER))).logger(new Slf4jLogger())
                .logLevel(Logger.Level.FULL).target(IceCreamService.class, "http://localhost:8089");

        try {
            client.authorization("test@github.com", "test-123456").get();
        } catch (Exception ex) {
            caughtTimeout = ex.getCause() instanceof ReadTimeoutException;
        }
        Assert.assertTrue("Timeout exception is expected: ", caughtTimeout);
        removeStub(stubMapping);
    }

    @Test
    public void connectionTimeoutFor10MsTest() {
        boolean caughtTimeout = false;
        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/authorization")).willReturn(aResponse().withFixedDelay(3000)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getHttpClientWithTimeout(10))
                .encoder(new FormEncoder(new JacksonEncoder(TestUtils.MAPPER))).logger(new Slf4jLogger())
                .logLevel(Logger.Level.FULL).target(IceCreamService.class, "http://9.162.252.200:9093");

        try {
            client.authorization("test@gihub.com", "test-123456").get();
        } catch (Exception ex) {
            caughtTimeout = ex.getCause() instanceof ConnectTimeoutException;
        }
        Assert.assertTrue("Connection timeout exception is expected: ", caughtTimeout);
        removeStub(stubMapping);
    }

    @Test
    public void readTimeoutFor10MsWithCircuitBreakerTest() {
        boolean caughtCircuitBreaker = false;
        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/authorization"))
                .willReturn(aResponse().withFixedDelay(3000)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getHttpClientWithTimeout(10))
                .encoder(new FormEncoder(new JacksonEncoder(TestUtils.MAPPER)))
                .circuitBreakerConfig(CircuitBreakerConfig.custom().failureRateThreshold(Circuit.failureRateThreshold)
                        .ringBufferSizeInClosedState(Circuit.ringBufferSizeInClosedState)
                        .ringBufferSizeInHalfOpenState(Circuit.ringBufferSizeInHalfOpenState)
                        .waitDurationInOpenState(Duration.ofSeconds(Circuit.waitDurationInOpenState)).build())
                .logger(new Slf4jLogger()).logLevel(Logger.Level.FULL).target(IceCreamService.class, "http://localhost:8089");

        try {
            List<CompletableFuture<?>> cfs = new ArrayList<>();
            for (int i = 0; i < 200; i++)
                client.authorization("test@github.com", "test-123456");

            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            client.authorization("test@github.com", "test-123456").get();
        } catch (Exception ex) {
            log.info(">>> Exception received: " + ex.getCause().getClass());
            Assert.assertTrue("Circuit breaker exception is expected: ", ex.getCause() instanceof CircuitBreakerOpenException);
            Uninterruptibles.sleepUninterruptibly(Circuit.waitDurationInOpenState, TimeUnit.SECONDS);
            try {
                client.authorization("test@github.com", "test-123456").get();
            } catch (Exception ex2) {
                caughtCircuitBreaker = ex2.getCause() instanceof ReadTimeoutException;
            }
        }

        Assert.assertEquals("Timeout exception is expected: ", true, caughtCircuitBreaker);
        removeStub(stubMapping);
    }

    public static class Circuit {
        static int failureRateThreshold = 12;
        static int ringBufferSizeInHalfOpenState = 10;
        static int ringBufferSizeInClosedState = 100;
        static int waitDurationInOpenState = 3;
    }

}
