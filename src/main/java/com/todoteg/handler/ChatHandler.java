package com.todoteg.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todoteg.message.IMessage;
import com.todoteg.messaging.MessageListener;
import com.todoteg.messaging.MessagingService;
import com.todoteg.security.JwtUtil;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Component
public class ChatHandler implements WebSocketHandler {
	
	private final ObjectMapper mapper;
	private final MessagingService service;
	private final MessageListener listener;
	private final JwtUtil jwtUtil;

	public ChatHandler(ObjectMapper mapper, MessagingService service, MessageListener listener, JwtUtil jwtUtil) {
		this.mapper = mapper;
		this.service = service;
		this.listener = listener;
		this.jwtUtil = jwtUtil;
	}

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		String token = extractToken(session);
		if (token == null) {
			return session.close();
		}

		String email = jwtUtil.extractUsername(token);
		Long userId = jwtUtil.extractUserId(token);

		session.getAttributes().put("auth_email", email);
		session.getAttributes().put("auth_userId", userId);

		return session.receive()
				.map(WebSocketMessage::getPayloadAsText)
				.map(this::toEvent)
				.flatMap(event -> listener.onMessage(event, session))
				.doFinally(signalType -> {
		            if (SignalType.ON_COMPLETE.equals(signalType)) {
		                listener.onDisconnect(session);
		            }
		        })
                .then();
	}

	private String extractToken(WebSocketSession session) {
		String query = session.getHandshakeInfo().getUri().getQuery();
		if (query == null) return null;
		return UriComponentsBuilder.newInstance()
				.query(query)
				.build()
				.getQueryParams()
				.getFirst("token");
	}

	private IMessage toEvent(String payload) {
		try {
			return mapper.readValue(payload, IMessage.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

}
