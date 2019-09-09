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
import feign.api.IceCreamServiceFallbacks;
import feign.api.NonInstantiablePredicate;
import feign.api.NonMatchingFallbackMethod;
import feign.api.domain.Bill;
import feign.api.domain.IceCreamOrder;
import feign.api.domain.OrderGenerator;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.CompletionException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static feign.api.IceCreamServiceFallbacks.EXCEPTION_MSG;
import static feign.api.IceCreamServiceFallbacks.fallbackCount;
import static org.assertj.core.api.Assertions.assertThat;

public class FallbackAsyncClientTest extends AbstractTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    public IceCreamServiceFallbacks client;
    private OrderGenerator generator = new OrderGenerator();

    @Before
    public void setupRestClient() {
        client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .encoder(new JacksonEncoder(TestUtils.MAPPER)).decoder(new JacksonDecoder(TestUtils.MAPPER))
                .logger(new Slf4jLogger()).logLevel(Logger.Level.FULL)
                .target(IceCreamServiceFallbacks.class, "http://localhost:8089");
    }

    @Test
    public void testSimpleGetFallbackEmpty_success() {

        int currFallbackCount = fallbackCount.get();

        StubMapping stubMapping = stubFor(get(urlEqualTo("/icecream/flavors")).withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")));

        client.getAvailableFlavors().whenComplete((flavors, throwable) -> assertThat(flavors).isEmpty()).join();

        Assert.assertEquals(currFallbackCount + 1, fallbackCount.get());
        removeStub(stubMapping);
    }

    @Test
    public void testMakeOrderFallback_success() {

        int currFallbackCount = fallbackCount.get();

        IceCreamOrder order = generator.generate();
        Bill bill = new Bill(0.2F);

        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/orders")).withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")));

        client.makeOrder(order)
                .whenComplete((bill1, throwable) -> Assertions.assertThat(bill1).isEqualToComparingFieldByFieldRecursively(bill))
                .join();

        Assert.assertEquals(currFallbackCount + 1, fallbackCount.get());
        removeStub(stubMapping);
    }

    @Test
    public void testMakeOrderFallbackIgnoreException_success() {

        int currFallbackCount = fallbackCount.get();
        IceCreamOrder order = generator.generate();

        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/orders")).withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")));

        expectedException.expect(CompletionException.class);
        client.makeOrderIgnoreFeignException(order)
                .whenComplete((b, throwable) -> Assertions.assertThat(throwable).isOfAnyClassIn(FeignException.class)).join();

        Assert.assertEquals(currFallbackCount, fallbackCount.get());
        removeStub(stubMapping);
    }

    @Test
    public void testMakeOrderFallbackIgnorePredicate_success() {

        int currFallbackCount = fallbackCount.get();
        IceCreamOrder order = generator.generate();

        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/orders")).withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")));

        expectedException.expect(CompletionException.class);
        client.makeOrderIgnorePredicate(order)
                .whenComplete((b, throwable) -> Assertions.assertThat(throwable).isOfAnyClassIn(FeignException.class)).join();

        Assert.assertEquals(currFallbackCount, fallbackCount.get());
        removeStub(stubMapping);
    }

    @Test
    public void testMakeOrderFallbackThrowsException_success() {

        int currFallbackCount = fallbackCount.get();

        IceCreamOrder order = generator.generate();

        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/orders")).withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")));

        expectedException.expect(CompletionException.class);
        client.makeOrder2(order).whenComplete((b, throwable) -> Assertions.assertThat(throwable)
                .isOfAnyClassIn(IllegalArgumentException.class).hasMessage(EXCEPTION_MSG)).join();

        Assert.assertEquals(currFallbackCount + 1, fallbackCount.get());
        removeStub(stubMapping);
    }

    @Test
    public void testCallFallbacksDirectlyAndException_success() {

        int currFallbackCount = fallbackCount.get();
        client.getAvailableFlavorsFallback(new IllegalArgumentException())
                .whenComplete((flavors, throwable) -> assertThat(flavors).isEmpty()).join();

        Bill bill = new Bill(0.2F);
        client.makeOrderFallback(null, new IllegalArgumentException())
                .whenComplete((bill1, throwable) -> Assertions.assertThat(bill).isEqualToComparingFieldByFieldRecursively(bill1))
                .join();

        Assert.assertEquals(currFallbackCount + 2, fallbackCount.get());
    }

    @Test
    public void testNonInstantiablePredicate_fail() {

        expectedException.expect(IllegalStateException.class);
        AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .encoder(new JacksonEncoder(TestUtils.MAPPER)).decoder(new JacksonDecoder(TestUtils.MAPPER))
                .logger(new Slf4jLogger()).logLevel(Logger.Level.FULL)
                .target(NonInstantiablePredicate.class, "http://localhost:8089");
    }

    @Test
    public void testNonMatchingFallback_fail() {

        expectedException.expect(IllegalStateException.class);
        AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .encoder(new JacksonEncoder(TestUtils.MAPPER)).decoder(new JacksonDecoder(TestUtils.MAPPER))
                .logger(new Slf4jLogger()).logLevel(Logger.Level.FULL)
                .target(NonMatchingFallbackMethod.class, "http://localhost:8089");
    }

}
