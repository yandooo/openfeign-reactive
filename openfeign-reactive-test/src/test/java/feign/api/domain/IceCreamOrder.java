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

import java.time.Instant;
import java.util.*;

@Data
public class IceCreamOrder {
    private static Random random = new Random();

    private int id; // order id
    private Map<Flavor, Integer> balls; // how much balls of flavor
    private Set<Mixin> mixins; // and some mixins ...
    private Instant orderTimestamp; // and give it to me right now !

    IceCreamOrder() {
        this(Instant.now());
    }

    IceCreamOrder(final Instant orderTimestamp) {
        this.id = random.nextInt();
        this.balls = new HashMap<>();
        this.mixins = new HashSet<>();
        this.orderTimestamp = orderTimestamp;
    }

    IceCreamOrder addBall(final Flavor ballFlavor) {
        final Integer ballCount = balls.containsKey(ballFlavor) ? balls.get(ballFlavor) + 1 : 1;
        balls.put(ballFlavor, ballCount);
        return this;
    }

    IceCreamOrder addMixin(final Mixin mixin) {
        mixins.add(mixin);
        return this;
    }

    IceCreamOrder withOrderTimestamp(final Instant orderTimestamp) {
        this.orderTimestamp = orderTimestamp;
        return this;
    }

}
