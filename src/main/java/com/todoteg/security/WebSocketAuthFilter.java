package com.todoteg.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebSocketAuthFilter implements WebFilter {

    private final JwtUtil jwtUtil;

    public WebSocketAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Only authenticate WebSocket and API paths
        if (!path.equals("/chat-socket") && !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        String token = request.getQueryParams().getFirst("token");
        // Also check Authorization header for REST endpoints
        if (token == null || token.isBlank()) {
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        if (token == null || token.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (!jwtUtil.validateToken(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String email = jwtUtil.extractUsername(token);
        Long userId = jwtUtil.extractUserId(token);

        exchange.getAttributes().put("auth_email", email);
        exchange.getAttributes().put("auth_userId", userId);

        return chain.filter(exchange);
    }
}
