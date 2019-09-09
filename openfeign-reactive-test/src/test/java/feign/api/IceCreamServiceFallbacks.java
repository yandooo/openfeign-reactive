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

package feign.api;

import feign.Fallback;
import feign.FeignException;
import feign.Headers;
import feign.RequestLine;
import feign.api.domain.Bill;
import feign.api.domain.Flavor;
import feign.api.domain.IceCreamOrder;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@Headers({"Accept: application/json"})
public interface IceCreamServiceFallbacks {

    String EXCEPTION_MSG = "Fallback throws an exception!";
    AtomicInteger fallbackCount = new AtomicInteger(0);

    @RequestLine("GET /icecream/flavors")
    @Fallback("getAvailableFlavorsFallback")
    CompletableFuture<Collection<Flavor>> getAvailableFlavors();

    @RequestLine("POST /icecream/orders")
    @Headers("Content-Type: application/json")
    @Fallback("makeOrderFallback")
    CompletableFuture<Bill> makeOrder(IceCreamOrder order);

    @RequestLine("POST /icecream/orders")
    @Headers("Content-Type: application/json")
    @Fallback("makeOrderFallback2")
    CompletableFuture<Bill> makeOrder2(IceCreamOrder order);

    @RequestLine("POST /icecream/orders")
    @Headers("Content-Type: application/json")
    @Fallback(value = "makeOrderFallback", ignoreExceptions = {FeignException.class})
    CompletableFuture<Bill> makeOrderIgnoreFeignException(IceCreamOrder order);

    @RequestLine("POST /icecream/orders")
    @Headers("Content-Type: application/json")
    @Fallback(value = "makeOrderFallback", ignorePredicate = TRUE_PREDICATE.class)
    CompletableFuture<Bill> makeOrderIgnorePredicate(IceCreamOrder order);

    default CompletableFuture<Collection<Flavor>> getAvailableFlavorsFallback(Throwable exception) {
        fallbackCount.incrementAndGet();
        final CompletableFuture<Collection<Flavor>> fb = new CompletableFuture<>();
        fb.complete(Collections.EMPTY_LIST);
        return fb;
    }

    default CompletableFuture<Bill> makeOrderFallback(IceCreamOrder order, Throwable exception) {
        fallbackCount.incrementAndGet();
        final CompletableFuture<Bill> fb = new CompletableFuture<>();
        fb.complete(new Bill(0.2F));
        return fb;
    }

    default CompletableFuture<Bill> makeOrderFallback2(IceCreamOrder order, Throwable exception) {
        fallbackCount.incrementAndGet();
        final CompletableFuture<Bill> fb = new CompletableFuture<>();
        fb.completeExceptionally(new IllegalArgumentException(EXCEPTION_MSG));
        return fb;
    }

    final class TRUE_PREDICATE implements Predicate<Throwable> {
        @Override
        public boolean test(Throwable t) {
            return true;
        }
    }
}
