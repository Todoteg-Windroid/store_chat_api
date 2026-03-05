package com.todoteg.messaging;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todoteg.message.IMessage;
import com.todoteg.message.impl.ChatMessage;
import com.todoteg.message.impl.EventType;
import com.todoteg.message.impl.JoinChatMessage;
import com.todoteg.message.impl.JoinMessage;
import com.todoteg.message.impl.LeaveChatMessage;
import com.todoteg.message.impl.LeaveMessage;
import com.todoteg.models.User;
import com.todoteg.repo.IChatMessageRepo;
import com.todoteg.service.IUserService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;



@Component
public class MessageListener {
	
	private static final Logger log = org.slf4j.LoggerFactory.getLogger(MessagingService.class);
	private final MessagingService service;
	private static Map<User, WebSocketSession> users = new HashMap<>();
	private final Sinks.Many<User> history = Sinks.many().replay().all();
	private final ObjectMapper mapper;
	Map<String, Sinks.One<Void>> stopSignals = new HashMap<>();
	Map<String, List<Boolean>> isActiveUserPair = new HashMap<>();
	private final IUserService serviceUser;
	private final AtomicBoolean isDataLoaded = new AtomicBoolean(false);
	private final IChatMessageRepo repoChatMessage;
	public MessageListener(MessagingService service, ObjectMapper mapper, IUserService serviceUser, IChatMessageRepo repoChatMessage) {
		this.service = service;
		this.mapper = mapper;
		this.serviceUser = serviceUser;
		this.repoChatMessage = repoChatMessage;
	}

	private String getAuthEmail(WebSocketSession session) {
		return (String) session.getAttributes().get("auth_email");
	}

	private Long getAuthUserId(WebSocketSession session) {
		return (Long) session.getAttributes().get("auth_userId");
	}

	public Mono<Void> onMessage(IMessage wsMessage, WebSocketSession session) {
		log.info("Message received: {}", wsMessage);
		EventType type = wsMessage.getType();
		switch(type) {
			case JOIN -> {
				JoinMessage message = (JoinMessage) wsMessage;
				String email = getAuthEmail(session);
				Long storeUserId = getAuthUserId(session);
				
				User user = User.builder()
						.setName(message.getSender())
						.setEmail(email)
						.setStoreUserId(storeUserId)
						.setStatus(true)
						.build();
				users.put(user, session);
				
				return serviceUser.findByEmail(email)
						.hasElement()
						.flatMap(hasUser -> {
							if(hasUser)	{
								return serviceUser.modificar(user)
										.doOnNext(usr -> history.tryEmitNext(usr)).then(handleExistingUser(user, session));
							}else {
								return handleNewUser(user, session);
							}
						}
						);
			}
			case JOIN_CHAT -> {
				JoinChatMessage message = (JoinChatMessage) wsMessage;
				String authEmail = getAuthEmail(session);
				// Enforce sender from token
				message.setSender(authEmail);
				
				Mono<Boolean> remitentePresente = serviceUser.findByEmail(message.getSender())
				        .hasElement()
				        .defaultIfEmpty(false);

				Mono<Boolean> receptorPresente = serviceUser.findByEmail(message.getRecipient())
				        .hasElement()
				        .defaultIfEmpty(false);
				
				return Mono.zip(remitentePresente, receptorPresente)
				        .flatMap(tuple -> {
				            boolean senderIsPresent = tuple.getT1();
				            boolean recipientIsPresent = tuple.getT2();

				            if (senderIsPresent && recipientIsPresent) {
				                Flux<WebSocketMessage> messages = service.getMenssages(message.getSender(), message.getRecipient())
				                        .map(msm -> session.textMessage(msm));
				                
								repoChatMessage.findBySenderAndRecipientAndIsReadFalse(message.getRecipient(), message.getSender())
								.flatMap(messageDB -> {
										messageDB.setRead(true);
										return service.onNext(messageDB, messageDB.getSender(), messageDB.getRecipient());
									})
								.subscribe();
								

				                Sinks.One<Void> stopSignal = stopSignals.compute(session.getId(), (k, j) -> Sinks.one());
				                String sessionId = service.getSessionId(message.getSender(), message.getRecipient());
				                isActiveUserPair.putIfAbsent(sessionId, new ArrayList<Boolean>(2));
				                
				                isActiveUserPair.get(sessionId).add(true);

				                return messages
				                        .takeUntilOther(stopSignal.asMono())
				                        .as(session::send)
				                        .and(service.onNext(message, message.getSender(), message.getRecipient()));
				            } else {
				                return Mono.error(new RuntimeException("El remitente o el receptor no están presentes"));
				            }
				        });
			}
			case LEAVE_CHAT ->{
				LeaveChatMessage message = (LeaveChatMessage) wsMessage;
				String authEmail = getAuthEmail(session);
				message.setSender(authEmail);
				
				Sinks.One<Void> stopSignal = stopSignals.get(session.getId());
				String sessionId = service.getSessionId(message.getSender(), message.getRecipient());
			    if (stopSignal != null) {
			        stopSignal.tryEmitEmpty();
			        isActiveUserPair.get(sessionId).remove(0);
			        return service.onNext(message, message.getSender(), message.getRecipient());
			    }
				return Mono.empty();
			}
			case CHAT -> {
				ChatMessage message = (ChatMessage) wsMessage;
				String authEmail = getAuthEmail(session);
				// Enforce sender from token
				message.setSender(authEmail);
				
				String sessionId = service.getSessionId(message.getSender(), message.getRecipient());
				if(isActiveUserPair.containsKey(sessionId) && isActiveUserPair.get(sessionId).size() == 2) {
					message.setRead(true);
				}
				return service.onNext(message, message.getSender(), message.getRecipient());
			}
			case LEAVE -> {
				String authEmail = getAuthEmail(session);
				
				Optional<User> userLeave = users.keySet().stream()
						.filter(user -> user.getEmail() != null && user.getEmail().equals(authEmail))
						.findFirst();
				if(userLeave.isPresent()) {
					users.remove(userLeave.get());
					User userModified = userLeave.get().toBuilder().setStatus(false).build();
					return serviceUser.modificar(userModified).then();					
				}
				
				return Mono.empty();
			}
		default -> throw new IllegalArgumentException("Unexpected value: " + type);
		}
		
	}
	
	private Mono<Void> handleExistingUser(User user, WebSocketSession session) {
		if (!isDataLoaded.getAndSet(true)) {
			return serviceUser.listar().doOnNext(usr -> {
				if(!usr.getEmail().equals(user.getEmail())) history.tryEmitNext(usr);
			})
					.then(sendSessionMessage(user, session));
		}
		return sendSessionMessage(user, session);
	}
	
	private Mono<Void> handleNewUser(User user, WebSocketSession session) {
		return serviceUser.registrar(user)
					.doOnNext(usr -> history.tryEmitNext(usr))
					.then(sendSessionMessage(user, session));
	}
	
	private Mono<Void> sendSessionMessage(User user, WebSocketSession session) {
		return session.send(
				history.asFlux()
				.filter(usr -> !usr.getEmail().equals(user.getEmail()))
				.map(this::toString)
				.map(msm-> session.textMessage(msm)));
	}
	
	public void onDisconnect(WebSocketSession session) {
		String authEmail = getAuthEmail(session);
		if (authEmail == null) return;

		Optional<User> userLeave = users.keySet().stream()
				.filter(user -> user.getEmail() != null && user.getEmail().equals(authEmail))
				.findFirst();

		if (userLeave.isPresent()) {
		    users.remove(userLeave.get());
		    User userModified = userLeave.get().toBuilder().setStatus(false).build();
		    serviceUser.modificar(userModified).doOnNext(usr -> history.tryEmitNext(usr)).subscribe();
		}
	}
	
	private String toString(User event) {
		try {
			return mapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
	
	
}