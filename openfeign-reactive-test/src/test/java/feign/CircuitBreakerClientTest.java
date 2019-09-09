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
import feign.api.IceCreamService;
import feign.jackson.JacksonDecoder;
import io.github.robwin.circuitbreaker.CircuitBreakerConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

public class CircuitBreakerClientTest extends AbstractTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testFindOrder404CircuitBreaker_error() throws ExecutionException, InterruptedException {

        StubMapping stubMapping = stubFor(get(urlEqualTo("/icecream/orders/123")).withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(404)));

        IceCreamService client = AsyncFeign.builder().asyncHttpClient(getOrCreateHttpClient())
                .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults()).decoder(new JacksonDecoder(TestUtils.MAPPER))
                .target(IceCreamService.class, "http://localhost:8089");

        client.findOrder(123).exceptionally(ex -> {
            assertThat(ex).isInstanceOf(FeignException.class).hasMessageContaining("404");
            return null;
        }).get();
        removeStub(stubMapping);
    }
}
