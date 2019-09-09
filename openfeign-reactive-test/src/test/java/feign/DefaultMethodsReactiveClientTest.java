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
import feign.api.DefaultMethodsIceCreamService;
import feign.api.domain.Flavor;
import feign.api.domain.Mixin;
import feign.api.domain.OrderGenerator;
import feign.jackson.JacksonDecoder;
import feign.slf4j.Slf4jLogger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

public class DefaultMethodsReactiveClientTest extends AbstractTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private OrderGenerator generator = new OrderGenerator();

    @Test
    public void testSimpleGetReactiveAndSynchronous_success() throws Exception {

        String flavorsStr = Arrays.stream(Flavor.values()).map(flavor -> "\"" + flavor + "\"")
                .collect(Collectors.joining(", ", "[ ", " ]"));

        String mixinsStr = Arrays.stream(Mixin.values()).map(flavor -> "\"" + flavor + "\"")
                .collect(Collectors.joining(", ", "[ ", " ]"));

        StubMapping stubMapping = stubFor(get(urlEqualTo("/icecream/flavors"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(flavorsStr)));

        StubMapping stubMapping1 = stubFor(get(urlEqualTo("/icecream/mixins"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(mixinsStr)));

        try {
            DefaultMethodsIceCreamService client = AsyncFeign.builder()
                    .asyncHttpClient(getOrCreateHttpClient())
                    .decoder(new JacksonDecoder(TestUtils.MAPPER)).logger(new Slf4jLogger()).logLevel(Logger.Level.FULL)
                    .target(DefaultMethodsIceCreamService.class, "http://localhost:8089");

            // reactor
            Collection<Flavor> flavorsFutureReactor = client.getAvailableFlavorsReactor().block();
            // rxJava
            Collection<Flavor> flavorsFutureRx = client.getAvailableFlavorsRx().blockingFirst();
            // synchronous
            Collection<Flavor> flavorsFutureSync = client.getAvailableFlavorsSync();

            assertThat(flavorsFutureReactor).hasSize(Flavor.values().length).containsAll(Arrays.asList(Flavor.values()));

            assertThat(flavorsFutureRx).hasSize(Flavor.values().length).containsAll(Arrays.asList(Flavor.values()));

            assertThat(flavorsFutureSync).hasSize(Flavor.values().length).containsAll(Arrays.asList(Flavor.values()));

        } catch (Exception e) {
            if (!e.getMessage().contains("private access required"))
                throw e;
        }

        removeStub(stubMapping);
        removeStub(stubMapping1);
    }
}
