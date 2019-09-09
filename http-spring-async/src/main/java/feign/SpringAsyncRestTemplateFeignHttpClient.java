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

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SpringAsyncRestTemplateFeignHttpClient implements AsyncFeignHttpClient {
    private final AsyncRestTemplate asyncRestTemplate;

    public SpringAsyncRestTemplateFeignHttpClient(final AsyncRestTemplate asyncRestTemplate) {
        if (asyncRestTemplate == null)
            throw new IllegalArgumentException("AsyncRestTemplate instance must not be null");
        this.asyncRestTemplate = asyncRestTemplate;
    }

    @Override
    public CompletableFuture<Response> execute(final Request request, final Request.Options options) {
        SettableListenableFuture<Response> feignResponse = new SettableListenableFuture<>();
        makeHttpClientRequest(request).addCallback(result -> feignResponse
                        .set(Response.builder().request(request).status(result.getStatusCode().value()).reason(result.getStatusCode().getReasonPhrase())
                                .headers(toFeignMap(result.getHeaders())).body(result.getBody()).build()),
                ex -> {
                    if (ex instanceof HttpStatusCodeException) {
                        HttpStatusCodeException e = (HttpStatusCodeException) ex;
                        feignResponse
                                .set(Response.builder().request(request).status(e.getStatusCode().value()).reason(e.getStatusCode().getReasonPhrase())
                                        .headers(new HashMap<>()).body(e.getResponseBodyAsByteArray()).build());
                    } else {
                        feignResponse.setException(ex);
                    }
                });
        return buildCompletableFuture(feignResponse);
    }

    private Map<String, Collection<String>> toFeignMap(HttpHeaders httpHeaders) {
        Map<String, Collection<String>> feignMap = new HashMap<>();
        if (httpHeaders.size() > 0)
            httpHeaders.entrySet().forEach(e -> feignMap.put(e.getKey(), e.getValue()));
        return feignMap;
    }

    private ListenableFuture<ResponseEntity<byte[]>> makeHttpClientRequest(final Request request) {

        final MultiValueMap<String, String> headers = new HttpHeaders();
        request.headers().entrySet().forEach(c -> headers.put(c.getKey(), new ArrayList<>(c.getValue())));

        HttpEntity<?> httpEntity = new HttpEntity(request.requestBody().asBytes(), headers);

        URI uri = UriComponentsBuilder.fromUriString(request.url()).build(true).toUri();

        return asyncRestTemplate.exchange(uri, httpMethodFromString(request.httpMethod().name()), httpEntity, byte[].class);
    }

    private <T> CompletableFuture<T> buildCompletableFuture(final ListenableFuture<T> listenableFuture) {
        CompletableFuture<T> completable = new CompletableFuture<T>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean result = listenableFuture.cancel(mayInterruptIfRunning);
                super.cancel(mayInterruptIfRunning);
                return result;
            }
        };

        listenableFuture.addCallback(result -> completable.complete(result), t -> completable.completeExceptionally(t));
        return completable;
    }

    private HttpMethod httpMethodFromString(final String methodName) {
        return HttpMethod.resolve(methodName);
    }
}
