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
import feign.api.BrokenIceCreamService;
import feign.api.IceCreamService;
import feign.api.ProductService;
import feign.api.domain.*;
import feign.form.FormEncoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import feign.template.UriUtils;
import io.github.robwin.circuitbreaker.CircuitBreakerConfig;
import io.github.robwin.retry.RetryConfig;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BaseHttpClientTest extends AbstractTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private OrderGenerator generator = new OrderGenerator();

    @Test
    public void testFormEncoded_success() throws ExecutionException, InterruptedException {

        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/authorization")).withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/x-www-form-urlencoded")));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .encoder(new FormEncoder(new JacksonEncoder(TestUtils.MAPPER))).logger(new Slf4jLogger())
                .logLevel(Logger.Level.FULL).target(IceCreamService.class, "http://localhost:8089");

        client.authorization("test@github.com", "test-123456").get();
        removeStub(stubMapping);
    }

    @Test
    public void testStandardMethods_success() {
        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .decoder(new JacksonDecoder(TestUtils.MAPPER)).logger(new Slf4jLogger()).logLevel(Logger.Level.FULL)
                .target(IceCreamService.class, "http://localhost:8089");

        client.toString();
        client.hashCode();
        client.equals(client);
    }

    @Test
    public void testMethodEncoderException_success() {
        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .decoder(new JacksonDecoder(TestUtils.MAPPER)).logger(new Slf4jLogger()).logLevel(Logger.Level.FULL)
                .target(IceCreamService.class, "http://localhost:8089");

        client.findOrderException(123);
    }

    @Test
    public void testSimpleGet_success() throws ExecutionException, InterruptedException {

        String flavorsStr = Arrays.stream(Flavor.values()).map(flavor -> "\"" + flavor + "\"")
                .collect(Collectors.joining(", ", "[ ", " ]"));

        String mixinsStr = Arrays.stream(Mixin.values()).map(flavor -> "\"" + flavor + "\"")
                .collect(Collectors.joining(", ", "[ ", " ]"));

        StubMapping stubMapping = stubFor(get(urlEqualTo("/icecream/flavors")).withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(flavorsStr)));

        StubMapping stubMapping1 = stubFor(get(urlEqualTo("/icecream/mixins")).withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(mixinsStr)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .decoder(new JacksonDecoder(TestUtils.MAPPER)).logger(new Slf4jLogger()).logLevel(Logger.Level.FULL)
                .target(IceCreamService.class, "http://localhost:8089");

        CompletableFuture<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();
        CompletableFuture<Collection<Mixin>> mixinsFuture = client.getAvailableMixins();

        Collection<Flavor> res1 = flavorsFuture.get();
        assertThat(res1).hasSize(Flavor.values().length).containsAll(Arrays.asList(Flavor.values()));

        Collection<Mixin> res2 = mixinsFuture.get();
        assertThat(res2).hasSize(Mixin.values().length).containsAll(Arrays.asList(Mixin.values()));
        removeStub(stubMapping);
        removeStub(stubMapping1);
    }

    @Test
    public void testFindOrder_success() throws ExecutionException, InterruptedException {

        IceCreamOrder order = generator.generate();
        int orderId = order.getId();
        String orderStr = TestUtils.encodeAsJsonString(order);

        StubMapping stubMapping = stubFor(get(urlEqualTo("/icecream/orders/" + orderId)).withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(orderStr)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .decoder(new JacksonDecoder(TestUtils.MAPPER)).target(IceCreamService.class, "http://localhost:8089");

        IceCreamOrder iceCreamOrder = client.findOrder(orderId).get();
        Assertions.assertThat(iceCreamOrder).isEqualToComparingFieldByFieldRecursively(order);

        removeStub(stubMapping);
    }

    @Test
    public void testFindOrder_404() throws ExecutionException, InterruptedException {

        StubMapping stubMapping = stubFor(get(urlEqualTo("/icecream/orders/123")).withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(404)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .decoder(new JacksonDecoder(TestUtils.MAPPER)).target(IceCreamService.class, "http://localhost:8089");

        client.findOrder(123).exceptionally(ex -> {
            assertThat(ex).isInstanceOf(FeignException.class).hasMessageContaining("404");
            return null;
        }).get();

        removeStub(stubMapping);
    }

    @Test
    public void testFindOrder404CustomDecoder_fail() throws ExecutionException, InterruptedException {

        StubMapping stubMapping = stubFor(get(urlEqualTo("/icecream/orders/123")).withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(404)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .errorDecoder((methodKey, response) -> {
                    throw new FeignException(500, "Unconditional fail - 00001");
                }).target(IceCreamService.class, "http://localhost:8089");

        client.findOrder(123).exceptionally(ex -> {
            assertThat(ex).isInstanceOf(FeignException.class).hasMessageContaining("00001");
            return null;
        }).get();

        removeStub(stubMapping);
    }

    @Test
    public void testFindOrderDecode404_success() throws ExecutionException, InterruptedException {

        StubMapping accept = stubFor(get(urlEqualTo("/icecream/orders/123")).withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(404)));

        IceCreamService client = AsyncFeign.builder().decode404()
                .asyncHttpClient(getOrCreateHttpClient()).decoder(new JacksonDecoder(TestUtils.MAPPER))
                .target(IceCreamService.class, "http://localhost:8089");

        assertThat(client.findOrder(123).get()).isNull();
        removeStub(accept);
    }

    @Test
    public void testMakeOrder_success() throws ExecutionException, InterruptedException {

        IceCreamOrder order = generator.generate();
        Bill bill = Bill.makeBill(order);
        String orderStr = TestUtils.encodeAsJsonString(order);
        String billStr = TestUtils.encodeAsJsonString(bill);

        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/orders"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(billStr)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .encoder(new JacksonEncoder(TestUtils.MAPPER)).decoder(new JacksonDecoder(TestUtils.MAPPER))
                .target(IceCreamService.class, "http://localhost:8089");

        Bill bill2 = client.makeOrder(order).get();
        Assertions.assertThat(bill2).isEqualToComparingFieldByFieldRecursively(bill2);

        removeStub(stubMapping);
    }

    @Test
    public void testPayBill_success() throws ExecutionException, InterruptedException {

        Bill bill = Bill.makeBill(generator.generate());
        String billStr = TestUtils.encodeAsJsonString(bill);

        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/bills/pay")).withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson(billStr)).willReturn(aResponse().withStatus(200)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .encoder(new JacksonEncoder(TestUtils.MAPPER)).target(IceCreamService.class, "http://localhost:8089");

        client.payBill(bill).get();
        removeStub(stubMapping);
    }

    @Test
    public void testPayBillCircuitBreakerAndRetry_success() throws ExecutionException, InterruptedException {

        Bill bill = Bill.makeBill(generator.generate());
        String billStr = TestUtils.encodeAsJsonString(bill);

        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/bills/pay")).withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson(billStr)).willReturn(aResponse().withStatus(200)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults()).retryConfig(RetryConfig.ofDefaults())
                .encoder(new JacksonEncoder(TestUtils.MAPPER)).target(IceCreamService.class, "http://localhost:8089");

        Map<String, Object> headers = Maps.newHashMap("custom", "123456");
        headers.put("list", Lists.newArrayList("1"));
        client.payBillWithMapHeaders(bill, headers).get();
        removeStub(stubMapping);
    }

    @Test
    public void testPayBillQueryMap_exception() throws ExecutionException, InterruptedException {
        StubMapping stubMapping = stubFor(get(urlEqualTo("/icecream/bills/query?custom=123456&list=1"))
                .withHeader("Content-Type", equalTo("application/json")).withQueryParam("custom", equalTo("123456"))
                .withQueryParam("list", equalTo("1")).willReturn(aResponse().withStatus(200)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults()).retryConfig(RetryConfig.ofDefaults())
                .encoder(new JacksonEncoder(TestUtils.MAPPER)).target(IceCreamService.class, "http://localhost:8089");

        Map<String, Object> query = Maps.newHashMap("custom", "123456");
        query.put("list", Lists.newArrayList("1"));
        client.payBillWithQueryMap(query).get();
        removeStub(stubMapping);
    }

    @Test
    public void testGetAvailableFlavors_returnsRawResultType() throws ExecutionException, InterruptedException {

        String flavorsStr = Arrays.stream(Flavor.values()).map(flavor -> "\"" + flavor + "\"")
                .collect(Collectors.joining(", ", "[ ", " ]"));

        StubMapping accept = stubFor(get(urlEqualTo("/icecream/flavors")).withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(flavorsStr)));

        ProductService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .decoder(new JacksonDecoder(TestUtils.MAPPER)).target(ProductService.class, "http://localhost:8089");

        CompletableFuture<Response> listenableFuture = client.getAvailableFlavors();
        listenableFuture.thenAccept(res -> {
            try {
                String content = new BufferedReader(new InputStreamReader(res.body().asInputStream())).lines()
                        .collect(Collectors.joining("\n"));

                assertTrue(res.status() == 200);
                assertEquals(content, flavorsStr);
            } catch (IOException ioException) {

            }
        });
        listenableFuture.get();

        removeStub(accept);
    }

    @Test
    public void testPayBill_returnsEmptyBodyResponse() throws ExecutionException, InterruptedException {

        Bill bill = Bill.makeBill(generator.generate());
        String billStr = TestUtils.encodeAsJsonString(bill);

        StubMapping stubMapping = stubFor(post(urlEqualTo("/icecream/bills/pay")).withHeader("Content-Type", equalTo("application/json"))
                .willReturn(aResponse().withStatus(200)));

        ProductService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .encoder(new JacksonEncoder(TestUtils.MAPPER)).target(ProductService.class, "http://localhost:8089");

        CompletableFuture<Response> listenableFuture = client.payBill(bill);
        listenableFuture.get();

        removeStub(stubMapping);

    }

    @Test
    public void testInstantiationBrokenContract_throwsException() {
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage(containsString("java.util.concurrent.CompletableFuture"));

        AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .target(BrokenIceCreamService.class, "http://localhost:8089");
    }

    @Test
    public void testNoDoubleUrlEncoding_success()
            throws ExecutionException, InterruptedException {

        IceCreamOrder order = generator.generate();
        int orderId = order.getId();
        String orderStr = TestUtils.encodeAsJsonString(order);
        String orderIdWithSpecialCharacters = "order=%+";
        String orderIdWithSpecialCharactersEncoded = UriUtils.pathEncode(orderIdWithSpecialCharacters, Util.UTF_8);

        StubMapping stubMapping = stubFor(get(urlEqualTo("/icecream/orders/custom/" + orderIdWithSpecialCharactersEncoded))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(orderStr)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .decoder(new JacksonDecoder(TestUtils.MAPPER)).target(IceCreamService.class, "http://localhost:8089");

        IceCreamOrder iceCreamOrder = client.findCustomOrder(orderIdWithSpecialCharacters).get();
        Assertions.assertThat(iceCreamOrder).isEqualToComparingFieldByFieldRecursively(order);

        removeStub(stubMapping);
    }
}
