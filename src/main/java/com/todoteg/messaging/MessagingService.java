package com.todoteg.messaging;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todoteg.message.IMessage;
import com.todoteg.message.impl.ChatMessage;
import com.todoteg.message.impl.EventType;
import com.todoteg.repo.IChatMessageRepo;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
@Service
public class MessagingService {
	private static final Logger log = org.slf4j.LoggerFactory.getLogger(MessagingService.class);
	
	@Autowired
	private IChatMessageRepo repo;
	private final ObjectMapper mapper;
	private static Map<String, Sinks.Many<String>> sinks = new HashMap<>();

	
	public MessagingService(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public Mono<Void> onNext(IMessage next, String remitente, String receptor) {
		String sessionId = getSessionId(remitente, receptor);
		if(next.getType() == EventType.CHAT) {
			//repo.save((ChatMessage) next).doOnNext(message -> this.onNext(message, sessionId)).subscribe();
			return repo.save((ChatMessage) next).doOnNext(message -> this.onNext(message, sessionId)).then();
		}
		this.onNext(next, sessionId);
		return Mono.empty();
	}
	
	public void onNext(IMessage next, String sessionId) {
		if(!sinks.containsKey(sessionId)) return;
		
	    synchronized (sinks.get(sessionId)) { // Asegúrate de que las emisiones sean seriales
	        try {
	            String payload = mapper.writeValueAsString(next);
	            sinks.get(sessionId).emitNext(payload, Sinks.EmitFailureHandler.FAIL_FAST);
	        } catch (JsonProcessingException e) {
	            log.error("Unable to send message {} to session {}", next, sessionId, e);
	        }
	    }
	}

	public Flux<String> getMenssages(String remitente, String receptor) {
		String sessionId = getSessionId(remitente, receptor);
	    if(!sinks.containsKey(sessionId)) {
	    	sinks.putIfAbsent(sessionId, Sinks.many().replay().all());
	    	return repo.findAllBySenderAndRecipient(remitente, receptor).doOnNext((message)-> this.onNext(message, sessionId)).thenMany(sinks.get(sessionId).asFlux());	    	
	    }
	    return sinks.get(sessionId).asFlux();
	}
	
	public String getSessionId(String remitente, String receptor) {
		return remitente.compareTo(receptor) < 0 ? remitente + "_" + receptor : receptor + "_" + remitente;
	}
}
