# OpenFeign-async

Non-blocking Feign HTTP Client port.
This project leverages Feign concise syntax for declarative REST API definitions.
Supported non-blocking back-ends are Reactor Netty and Spring AsyncRestTemplate.

Currently library internals hardly rely on `java.util.concurrent.CompletableFuture`.
It possible to add Reactor/rxJava return types for the methods in the future. 
For now, use default methods to convert java future into the reactive library alternatives.

It turned out this proposal [isn't the first attempt](https://github.com/OpenFeign/feign-vertx) 
to improve Feign client. 
So the library is refactored to incorporate some ideas from other open-source materials.

## Usage

In the project gradle

```gradle
    compile group: 'openfeign-reactive', name: 'openfeign-reactive-core', version: "+"

    // use Jackson encoder / decoder
    compile group: 'io.github.openfeign', name: 'feign-jackson', version: "$LIB_FEIGN"
    compile group: 'io.github.openfeign', name: 'feign-slf4j', version: "$LIB_FEIGN"
        
    // SpringFramework AsyncRestTemplate. Make sure netty is compatible with your Spring version    
    compile group: 'io.netty', name: 'netty-all', version: "$LIB_NETTY_ALL"
    compile group: 'openfeign-reactive', name: 'http-spring-async', version: "+"
    
    // Reactor Netty
    compile group: 'openfeign-reactive', name: 'http-reactor-netty', version: "+"
```

Expected feign version is `10.+`. Netty binaries expected are `4.+`.

Write Feign API as usual, but every method of interface **must** return `java.util.concurrent.CompletableFuture`.

See [Feign official documentation](https://github.com/OpenFeign/feign) how to write REST clients code.

Back-ends are fully customizable REST HTTP clients.
Additional parameters like connect/read timeouts can be pre-configured before passing the client to a Feign proxy.

```java
@Headers({ "Accept: application/json" })
interface IceCreamService {

  @RequestLine("GET /icecream/flavors")
  CompletableFuture<Collection<Flavor>> getAvailableFlavors();

  @RequestLine("GET /icecream/orders/{orderId}")
  CompletableFuture<IceCreamOrder> findOrder(@Param("orderId") int orderId);
}
```

### Spring AsyncRestTemplate back-end

```java
final Netty4ClientHttpRequestFactory netty4ClientHttpRequestFactory = new Netty4ClientHttpRequestFactory();
netty4ClientHttpRequestFactory.setReadTimeout(timeout);
netty4ClientHttpRequestFactory.setConnectTimeout(timeout);
                
AsyncRestTemplate asyncRestTemplate = new AsyncRestTemplate(netty4ClientHttpRequestFactory);

IceCreamService iceCreamService = AsyncFeign
    .builder()
    .asyncHttpClient(asyncRestTemplate)
    .encoder(new JacksonEncoder())
    .decoder(new JacksonDecoder())
    .target(IceCreamService.class, "http://github.com");
    
CompletableFuture<Collection<Flavor>> flavorsFuture = iceCreamService.getAvailableFlavors();
```

### Reactor Netty back-end

```java
httpClient = HttpClient.create()
                       .doOnRequest((httpClientRequest, conn) ->
                                conn.addHandler(new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS)))
                       .tcpConfiguration(tcpClient ->
                                tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout));

HttpClient reactorHttpClient = new ReactorNettyFeignHttpClient(httpClient);

IceCreamService iceCreamService = AsyncFeign
    .builder()
    .asyncHttpClient(reactorHttpClient)
    .encoder(new JacksonEncoder())
    .decoder(new JacksonDecoder())
    .target(IceCreamService.class, "http://github.com);
    
CompletableFuture<Collection<Flavor>> flavorsFuture = iceCreamService.getAvailableFlavors();
```

## Original binaries compatibility

Most Feign APIs created based on the original version of Feign should work.
Although there are few issues exists due to changed computation paradigm to be non-blocking:

 - Logger does not report properly execution time (there is another async interceptor added to provide the same) 
 - Hystrix integration is not recommended to use (not even tested if it works or not). Please use different lightweight circuit breaker and retry mechanisms provided by the library
 - Feign encoders/decoders, interceptors are designed to work with complete data. 
    This contract has to be accepted in this library implementation. 
    So while there is a statement for non-blocking HTTP calls execution it's still 99% reactive.
 
### CircuitBreaker & Retry

```java
import io.github.robwin.circuitbreaker.CircuitBreakerConfig;
import io.github.robwin.retry.RetryConfig;

CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()...
RetryConfig rConfig = RetryConfig.custom()...

IceCreamService iceCreamService = AsyncFeign
    .builder()
    .circuitBreakerConfig(cbConfig)
    .retryConfig(rConfig)...
    
CompletableFuture<Collection<Flavor>> flavorsFuture = iceCreamService.getAvailableFlavors();

```

### Method fallback

Use `feign.Fallback` annotation to define a fallback method for the client call.

The defined fallback method must have the same input parameters as the original method plus additional `Throwable` argument which must be the last input argument.
The return type must be the same as for the original method. Please do not try to overload the fallback method - it's not supported by the library yet.

There is a list of ignorable exception which is inspected in order to determine if fallback should be called.
If you need more flexibility use to ignore predicate function which returns boolean decision (`true` means fallback should be ignored and`false` otherwise).


```java
@Headers({"Accept: application/json"})
public interface IceCreamServiceFallbacks {

    @RequestLine("POST /icecream/orders")
    @Headers("Content-Type: application/json")
    @Fallback("makeOrderFallback")
    CompletableFuture<Bill> makeOrder(IceCreamOrder order);


    default CompletableFuture<Bill> makeOrderFallback(IceCreamOrder order, Throwable exception) {
        final CompletableFuture<Bill> fb = new CompletableFuture<>();
        fb.complete(new Bill(0.2F));
        return fb;
    }

}
```


## Reactive return types

In the project gradle add reactive library dependency (Reactor, rxJava).
No need for Reactor dependencies in case of Reactor Netty back-end usage.

Use default interface methods to add custom built-in functionality:

```java
@Headers({"Accept: application/json"})
public interface IceCreamService {

    @RequestLine("GET /icecream/flavors")
    CompletableFuture<Collection<Flavor>> getAvailableFlavors();

    // Reactor
    default Mono<Collection<Flavor>> getAvailableFlavorsReactor() {
        return Mono.fromFuture(getAvailableFlavors());
    }

    // RxJava
    default Observable<Collection<Flavor>> getAvailableFlavorsRx() {
        return Observable.fromFuture(getAvailableFlavors());
    }

    // synchronous implementation
    default Collection<Flavor> getAvailableFlavorsSync() throws ExecutionException, InterruptedException {
        return getAvailableFlavors().get();
    }
}
```

or convert to the reactive flows straight in the code:


```java

@Headers({"Accept: application/json"})
public interface IceCreamService {

    @RequestLine("GET /icecream/flavors")
    CompletableFuture<Collection<Flavor>> getAvailableFlavors();
}

...

 Mono<Collection<Flavor>> monoFlavors = Mono.fromFuture(getAvailableFlavors());

 Observable<Collection<Flavor>> observableFlavors =  Observable.fromFuture(getAvailableFlavors());

```