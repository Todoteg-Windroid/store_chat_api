package com.todoteg.handler;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import reactor.core.publisher.Sinks;

@Component
public class ChatHandler implements WebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);
	private final ObjectMapper mapper;
	private final MessagingService service;
	private final MessageListener listener;
	private final JwtUtil jwtUtil;
	private final List<String> adminEmails;

	public ChatHandler(ObjectMapper mapper, MessagingService service, MessageListener listener, JwtUtil jwtUtil,
			@Value("${chat.admin.emails}") String adminEmailsStr) {
		this.mapper = mapper;
		this.service = service;
		this.listener = listener;
		this.jwtUtil = jwtUtil;
		this.adminEmails = Arrays.asList(adminEmailsStr.split(","));
	}

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		String token = extractToken(session);
		if (token == null) {
			return session.close();
		}

		String email = jwtUtil.extractUsername(token);
		Long userId = jwtUtil.extractUserId(token);
		boolean isAdmin = adminEmails.contains(email.trim());

		session.getAttributes().put("auth_email", email);
		session.getAttributes().put("auth_userId", userId);
		session.getAttributes().put("is_admin", isAdmin);

		// Per-session output sink — all outbound messages flow through this single channel
		Sinks.Many<String> outputSink = Sinks.many().multicast().onBackpressureBuffer();
		session.getAttributes().put("outputSink", outputSink);

		Mono<Void> receive = session.receive()
				.map(WebSocketMessage::getPayloadAsText)
				.map(this::toEvent)
				.flatMap(event -> listener.onMessage(event, session)
						.onErrorResume(e -> {
							log.error("Error processing WS message: {}", e.getMessage());
							return Mono.empty();
						}))
				.doFinally(signalType -> listener.onDisconnect(session))
				.then();

		Mono<Void> send = session.send(
				outputSink.asFlux().map(session::textMessage));

		return Mono.zip(receive, send).then();
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
