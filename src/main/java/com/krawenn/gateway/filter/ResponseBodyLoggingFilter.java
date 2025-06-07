package com.krawenn.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.reactivestreams.Publisher;

import java.nio.charset.StandardCharsets;

@Component
public class ResponseBodyLoggingFilter implements GlobalFilter, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(ResponseBodyLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange.mutate().response(new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (getDelegate().getHeaders().getContentType() != null &&
                        MediaType.APPLICATION_JSON.isCompatibleWith(getDelegate().getHeaders().getContentType())) {
                    Flux<? extends DataBuffer> flux = Flux.from(body);
                    return super.writeWith(flux.buffer().map(dataBuffers -> {
                        DataBuffer joined = exchange.getResponse().bufferFactory().join(dataBuffers);
                        byte[] bytes = new byte[joined.readableByteCount()];
                        joined.read(bytes);
                        DataBufferUtils.release(joined);
                        String responseBody = new String(bytes, StandardCharsets.UTF_8);
                        logger.info("Response body: {}", responseBody);
                        return exchange.getResponse().bufferFactory().wrap(bytes);
                    }));
                }
                return super.writeWith(body);
            }
        }).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }
} 