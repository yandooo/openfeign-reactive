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

import com.google.common.util.concurrent.Uninterruptibles;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AsyncUtilsTest {

    @Test
    public void executionTracerSuccessImmediate() {
        CompletableFuture<Object> success = CompletableFuture.supplyAsync(() -> "success");

        FeignCompletableFuture<Object> cf = AsyncUtils.executionTracerIfAny(
                Request.create(Request.HttpMethod.POST, "https://bla.com", new HashMap<>(), null, Charset.defaultCharset()), success);

        cf.join();
        Assert.assertNotNull("StopWatch must be set", cf.getFeignContext().getExecutionMillis());
        Assert.assertNotNull("StopWatch must be set", cf.getFeignContext().getRequest());
    }

    @Test
    public void executionTracerSuccess3sec() {
        CompletableFuture<Object> success = CompletableFuture.supplyAsync(() -> {
            Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
            return "success";
        });

        FeignCompletableFuture<Object> cf = AsyncUtils.executionTracerIfAny(
                Request.create(Request.HttpMethod.POST, "https://bla.com", new HashMap<>(), null, Charset.defaultCharset()), success);

        cf.join();
        Assert.assertNotNull("StopWatch must be set", cf.getFeignContext().getExecutionMillis());
        Assert.assertNotNull("StopWatch must be set", cf.getFeignContext().getRequest());
    }

    @Test
    public void executionTracerSuccess5sec() {
        CompletableFuture<Object> success = CompletableFuture.supplyAsync(() -> {
            Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
            return "success";
        });

        FeignCompletableFuture<Object> cf = AsyncUtils.executionTracerIfAny(
                Request.create(Request.HttpMethod.POST, "https://bla.com", new HashMap<>(), null, Charset.defaultCharset()), success);

        cf.join();
        Assert.assertNotNull("StopWatch must be set", cf.getFeignContext().getExecutionMillis());
        Assert.assertNotNull("StopWatch must be set", cf.getFeignContext().getRequest());
    }

    @SuppressWarnings("PMD")
    @Test(expected = RuntimeException.class)
    public void executionTracerFailure() throws RuntimeException {
        CompletableFuture<Object> fail = CompletableFuture.supplyAsync(() -> {
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            throw new RuntimeException("Always fail");
        });

        FeignCompletableFuture<Object> cf = AsyncUtils.executionTracerIfAny(
                Request.create(Request.HttpMethod.POST, "https://bla.com", new HashMap<>(), null, Charset.defaultCharset()), fail);

        cf.whenComplete((o, throwable) -> {
            Assert.assertNotNull(throwable);
            Assert.assertNotNull("StopWatch must be set", cf.getFeignContext().getExecutionMillis());
            Assert.assertNotNull("StopWatch must be set", cf.getFeignContext().getRequest());
        }).join();
    }

}
