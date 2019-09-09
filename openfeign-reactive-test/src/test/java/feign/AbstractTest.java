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

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.web.client.AsyncRestTemplate;
import reactor.netty.http.client.HttpClient;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static feign.AbstractTest.HTTP_CLIENT_IMPL.REACTOR_NETTY;
import static feign.AbstractTest.HTTP_CLIENT_IMPL.SPRING_ASYNC;

@RunWith(Parameterized.class)
public abstract class AbstractTest {

    @Parameterized.Parameter(0)
    public static HTTP_CLIENT_IMPL httpClientType;

    private static Netty4ClientHttpRequestFactory requestFactory;
    private static AsyncRestTemplate asyncRestTemplate;
    private static AsyncFeignHttpClient feignHttpClient;
    private static HttpClient httpClient;

    @Parameterized.Parameters(name = "{index}: Test with HTTP_CLIENT_IMPL={0}")
    public static Collection<Object[]> data() {
        Object[][] data = new Object[][]{{SPRING_ASYNC}, {REACTOR_NETTY}};
        return Arrays.asList(data);
    }

    @AfterClass
    public static void closeHttpClient() throws InterruptedException {
        switch (httpClientType) {
            case SPRING_ASYNC:
                requestFactory.destroy();
                asyncRestTemplate = null;
                feignHttpClient = null;
                break;
            case REACTOR_NETTY:
                httpClient = null;
                break;
            default:
                throw new IllegalArgumentException("Unknown rest client type");
        }
    }

    @Before
    public void setupHttpClient() {
        getOrCreateHttpClient();
    }

    protected AsyncFeignHttpClient getOrCreateHttpClient() {

        switch (httpClientType) {
            case SPRING_ASYNC:
                requestFactory = new Netty4ClientHttpRequestFactory();
                asyncRestTemplate = new AsyncRestTemplate(requestFactory);
                feignHttpClient = new SpringAsyncRestTemplateFeignHttpClient(asyncRestTemplate);
                break;
            case REACTOR_NETTY:
                httpClient = HttpClient.create();
                feignHttpClient = new ReactorNettyFeignHttpClient(httpClient);

                break;
            default:
                throw new IllegalArgumentException("Unknown rest client type");
        }

        return feignHttpClient;
    }

    public AsyncFeignHttpClient getHttpClientWithTimeout(int timeout) {
        AsyncFeignHttpClient feignHttpClient = null;
        switch (httpClientType) {
            case SPRING_ASYNC:
                final Netty4ClientHttpRequestFactory netty4ClientHttpRequestFactory = new Netty4ClientHttpRequestFactory();
                netty4ClientHttpRequestFactory.setReadTimeout(timeout);
                netty4ClientHttpRequestFactory.setConnectTimeout(timeout);
                final AsyncRestTemplate asyncRestTemplate = new AsyncRestTemplate(netty4ClientHttpRequestFactory);

                feignHttpClient = new SpringAsyncRestTemplateFeignHttpClient(asyncRestTemplate);

                break;
            case REACTOR_NETTY:
                httpClient = HttpClient.create()
                        .doOnRequest((httpClientRequest, conn) ->
                                conn.addHandler(new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS)))
                        .tcpConfiguration(tcpClient ->
                                tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout));
                feignHttpClient = new ReactorNettyFeignHttpClient(httpClient);

                break;
            default:
                throw new IllegalArgumentException("Unknown rest client type");
        }

        return feignHttpClient;
    }

    public enum HTTP_CLIENT_IMPL {
        SPRING_ASYNC, REACTOR_NETTY
    }
}
