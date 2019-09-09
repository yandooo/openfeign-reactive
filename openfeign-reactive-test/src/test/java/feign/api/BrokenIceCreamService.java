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

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.api.domain.Bill;
import feign.api.domain.Flavor;
import feign.api.domain.IceCreamOrder;
import feign.api.domain.Mixin;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface BrokenIceCreamService {

    @RequestLine("GET /icecream/flavors")
    CompletableFuture<Collection<Flavor>> getAvailableFlavors();

    @RequestLine("GET /icecream/mixins")
    CompletableFuture<Collection<Mixin>> getAvailableMixins();

    @RequestLine("POST /icecream/orders")
    @Headers("Content-Type: application/json")
    CompletableFuture<Bill> makeOrder(IceCreamOrder order);

    /**
     * doesn't respects contract
     */
    @RequestLine("GET /icecream/orders/{orderId}")
    IceCreamOrder findOrder(@Param("orderId") int orderId);
}
