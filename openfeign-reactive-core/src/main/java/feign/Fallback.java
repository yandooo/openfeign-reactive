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

import java.lang.annotation.Retention;
import java.util.function.Predicate;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@java.lang.annotation.Target(METHOD)
@Retention(RUNTIME)
public @interface Fallback {

    /**
     * Fallback method name which has same input parameters as
     * original method with additional last argument being {@code Throwable}
     */
    String value() default "";

    /**
     * List of ignorable exceptions
     */
    Class<? extends Throwable>[] ignoreExceptions() default {};

    /**
     * Predicate class to evaluate exception and decide if fallback should be triggered
     */
    Class<? extends Predicate<? extends Throwable>> ignorePredicate() default FALSE_PREDICATE.class;

    final class FALSE_PREDICATE implements Predicate<Throwable> {
        @Override
        public boolean test(Throwable t) {
            return false;
        }
    }
}
