package in.aimlabs.gaming.operator.aggregator.controller;

import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public class ApiUtil {
    public static Mono<Void> getExampleResponse(ServerWebExchange exchange, String example) {
        return exchange.getResponse().writeWith(Mono.just(new DefaultDataBufferFactory().wrap(example.getBytes(StandardCharsets.UTF_8))));
    }
}
