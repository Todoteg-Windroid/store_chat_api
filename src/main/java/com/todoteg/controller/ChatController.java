package com.todoteg.controller;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

@Configuration
public class ChatController {

	@Autowired
	private WebSocketHandler handler;
	
	@Bean
	public HandlerMapping handlerMapping() {
		return new SimpleUrlHandlerMapping(
				Map.of("/chat-socket", handler), 1
		);
	}
}
