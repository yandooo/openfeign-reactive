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

package feign.api.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class Bill {
    private static final Map<Integer, Float> PRICES = new HashMap<>();
    private static final float MIXIN_PRICE = (float) 0.6; // price per mixin

    static {
        PRICES.put(1, (float) 2.00); // two euros for one ball (expensive!)
        PRICES.put(3, (float) 2.85); // 2.85€ for 3 balls
        PRICES.put(5, (float) 4.30); // 4.30€ for 5 balls
        PRICES.put(7, (float) 5); // only five euros for seven balls! Wow
    }

    private Float price;

    public Bill(final Float price) {
        this.price = price;
    }

    public static Bill makeBill(final IceCreamOrder order) {
        int nbBalls = order.getBalls().values().stream().mapToInt(Integer::intValue).sum();
        Float price = PRICES.get(nbBalls) + order.getMixins().size() * MIXIN_PRICE;
        return new Bill(price);
    }
}
