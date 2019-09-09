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
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Data
public class OrderGenerator {
    private static final int[] BALLS_NUMBER = {1, 3, 5, 7};
    private static final int[] MIXIN_NUMBER = {1, 2, 3};

    private static final Random random = new Random();

    public IceCreamOrder generate() {
        final IceCreamOrder order = new IceCreamOrder();
        final int nbBalls = peekBallsNumber();
        final int nbMixins = peekMixinNumber();

        IntStream.rangeClosed(1, nbBalls).mapToObj(i -> this.peekFlavor()).forEach(order::addBall);

        IntStream.rangeClosed(1, nbMixins).mapToObj(i -> this.peekMixin()).forEach(order::addMixin);

        return order;
    }

    public Collection<IceCreamOrder> generate(int n) {
        Instant now = Instant.now();

        List<Instant> orderTimestamps = IntStream.range(0, n).mapToObj(minutes -> now.minus(minutes, ChronoUnit.MINUTES))
                .collect(Collectors.toList());

        return IntStream.range(0, n).mapToObj(i -> this.generate().withOrderTimestamp(orderTimestamps.get(i)))
                .collect(Collectors.toList());
    }

    private int peekBallsNumber() {
        return BALLS_NUMBER[random.nextInt(BALLS_NUMBER.length)];
    }

    private int peekMixinNumber() {
        return MIXIN_NUMBER[random.nextInt(MIXIN_NUMBER.length)];
    }

    private Flavor peekFlavor() {
        return Flavor.values()[random.nextInt(Flavor.values().length)];
    }

    private Mixin peekMixin() {
        return Mixin.values()[random.nextInt(Mixin.values().length)];
    }
}
