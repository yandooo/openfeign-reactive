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

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ReactorNettyFeignHttpClient implements AsyncFeignHttpClient {

    private final HttpClient httpClient;

    public ReactorNettyFeignHttpClient(final HttpClient httpClient) {
        if (httpClient == null)
            throw new IllegalArgumentException("Reactor 'HttpClient' instance must not be null");
        this.httpClient = httpClient;
    }

    @Override
    public CompletableFuture<Response> execute(Request request, Request.Options options) {
        return makeHttpClientRequest(request).toFuture();
    }

    private Map<String, Collection<String>> toFeignMap(HttpHeaders httpHeaders) {
        Map<String, Collection<String>> feignMap = new HashMap<>();
        if (httpHeaders.size() > 0)
            httpHeaders.entries().forEach(e -> feignMap.put(e.getKey(), Collections.singletonList(e.getValue())));
        return feignMap;
    }

    private Mono<Response> makeHttpClientRequest(final Request request) {
        final Optional<byte[]> optionalBytes = Optional.ofNullable((request.requestBody().asBytes()));
        return httpClient
                .headers(h -> request.headers().entrySet().forEach(c -> h.set(c.getKey(), new ArrayList<>(c.getValue()))))
                .request(httpMethodFromString(request.httpMethod().name()))
                .uri(request.url())
                .send(ByteBufFlux.fromInbound(Mono.just(optionalBytes.orElse(new byte[]{}))))
                .responseSingle((r, b) ->
                        b.asByteArray()
                                .defaultIfEmpty(new byte[]{})
                                .map(bytes -> Response.builder()
                                        .request(request)
                                        .status(r.status().code())
                                        .reason(r.status().reasonPhrase())
                                        .headers(toFeignMap(r.responseHeaders()))
                                        .body(bytes).build())
                );
    }

    private HttpMethod httpMethodFromString(final String methodName) {
        return HttpMethod.valueOf(methodName);
    }

}
