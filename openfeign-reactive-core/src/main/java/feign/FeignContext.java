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

import java.util.HashMap;
import java.util.Map;

public final class FeignContext {

    public static final String EXEC_MILLIS = "stopwatch";
    public static final String REQUEST = "request";

    private Map<String, Object> props;

    public FeignContext() {
        this(null);
    }

    public FeignContext(Map<String, Object> props) {
        this.props = props == null ? new HashMap<>() : props;
    }

    public Object get(String key) {
        return key != null ? props.get(key) : null;
    }

    public void set(String key, Object val) {
        props.put(key, val);
    }

    public Map<String, Object> getProps() {
        return new HashMap<>(props);
    }

    public long getExecutionMillis() {
        return (long) get(EXEC_MILLIS);
    }

    public FeignContext setExecutionMillis(final long executionMillis) {
        set(EXEC_MILLIS, executionMillis);
        return this;
    }

    public Request getRequest() {
        return (Request) get(REQUEST);
    }

    public FeignContext setRequest(final Request request) {
        set(REQUEST, request);
        return this;
    }

}
