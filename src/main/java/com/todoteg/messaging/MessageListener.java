package com.todoteg.messaging;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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
	private final ObjectMapper mapper;
	Map<String, Sinks.One<Void>> stopSignals = new HashMap<>();
	Map<String, List<Boolean>> isActiveUserPair = new HashMap<>();
	private final IUserService serviceUser;
	private final IChatMessageRepo repoChatMessage;
	private final List<String> adminEmails;

	public MessageListener(MessagingService service, ObjectMapper mapper, IUserService serviceUser,
			IChatMessageRepo repoChatMessage, @Value("${chat.admin.emails}") String adminEmailsStr) {
		this.service = service;
		this.mapper = mapper;
		this.serviceUser = serviceUser;
		this.repoChatMessage = repoChatMessage;
		this.adminEmails = Arrays.asList(adminEmailsStr.split(","));
	}

	private String getAuthEmail(WebSocketSession session) {
		return (String) session.getAttributes().get("auth_email");
	}

	private Long getAuthUserId(WebSocketSession session) {
		return (Long) session.getAttributes().get("auth_userId");
	}

	private boolean isAdmin(WebSocketSession session) {
		return Boolean.TRUE.equals(session.getAttributes().get("is_admin"));
	}

	private String getFirstAdminEmail() {
		return adminEmails.isEmpty() ? null : adminEmails.get(0).trim();
	}

	@SuppressWarnings("unchecked")
	private Sinks.Many<String> getOutputSink(WebSocketSession session) {
		return (Sinks.Many<String>) session.getAttributes().get("outputSink");
	}

	/** Send a user update JSON to all connected admin sessions */
	private void notifyAdminsOfUserUpdate(User user) {
		String json = toJson(user);
		users.entrySet().stream()
				.filter(e -> Boolean.TRUE.equals(e.getKey().getIsAdmin()))
				.forEach(e -> {
					Sinks.Many<String> sink = getOutputSink(e.getValue());
					if (sink != null) sink.tryEmitNext(json);
				});
	}

	public Mono<Void> onMessage(IMessage wsMessage, WebSocketSession session) {
		log.info("Message received: {}", wsMessage);
		EventType type = wsMessage.getType();
		switch(type) {
			case JOIN -> {
				JoinMessage message = (JoinMessage) wsMessage;
				String email = getAuthEmail(session);
				Long storeUserId = getAuthUserId(session);
				boolean admin = isAdmin(session);
				
				User user = User.builder()
						.setName(message.getSender())
						.setEmail(email)
						.setStoreUserId(storeUserId)
						.setStatus(true)
						.setIsAdmin(admin)
						.build();
				users.put(user, session);
				
				return serviceUser.findByEmail(email)
						.hasElement()
						.flatMap(hasUser -> {
							Mono<User> saveOp = hasUser
									? serviceUser.modificar(user)
									: serviceUser.registrar(user);
							return saveOp
									.doOnNext(usr -> notifyAdminsOfUserUpdate(usr))
									.then(admin ? sendAllUsersToAdmin(user, session) : Mono.empty());
						});
			}
			case JOIN_CHAT -> {
				JoinChatMessage message = (JoinChatMessage) wsMessage;
				String authEmail = getAuthEmail(session);
				message.setSender(authEmail);
				
				// If client, force recipient to admin
				if (!isAdmin(session)) {
					String adminEmail = getFirstAdminEmail();
					if (adminEmail == null) return Mono.error(new RuntimeException("No admin configured"));
					message.setRecipient(adminEmail);
				}
				
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
				                Sinks.Many<String> outputSink = getOutputSink(session);
				                Flux<String> messages = service.getMenssages(message.getSender(), message.getRecipient());
				                
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

				                // Subscribe to chat messages and forward to output sink
				                messages.takeUntilOther(stopSignal.asMono())
				                		.subscribe(
				                				msg -> outputSink.tryEmitNext(msg),
				                				err -> log.error("Error in message stream: {}", err.getMessage())
				                		);

				                return service.onNext(message, message.getSender(), message.getRecipient());
				            } else {
				                log.warn("JOIN_CHAT failed: sender={} ({}), recipient={} ({})",
				                		message.getSender(), senderIsPresent,
				                		message.getRecipient(), recipientIsPresent);
				                return Mono.empty();
				            }
				        });
			}
			case LEAVE_CHAT ->{
				LeaveChatMessage message = (LeaveChatMessage) wsMessage;
				String authEmail = getAuthEmail(session);
				message.setSender(authEmail);
				
				// If client, force recipient to admin
				if (!isAdmin(session)) {
					String adminEmail = getFirstAdminEmail();
					if (adminEmail != null) message.setRecipient(adminEmail);
				}
				
				Sinks.One<Void> stopSignal = stopSignals.get(session.getId());
				String sessionId = service.getSessionId(message.getSender(), message.getRecipient());
			    if (stopSignal != null) {
			        stopSignal.tryEmitEmpty();
			        if (isActiveUserPair.containsKey(sessionId)) {
			        	isActiveUserPair.get(sessionId).remove(0);
			        }
			        return service.onNext(message, message.getSender(), message.getRecipient());
			    }
				return Mono.empty();
			}
			case CHAT -> {
				ChatMessage message = (ChatMessage) wsMessage;
				String authEmail = getAuthEmail(session);
				message.setSender(authEmail);
				
				// If client, force recipient to admin
				if (!isAdmin(session)) {
					String adminEmail = getFirstAdminEmail();
					if (adminEmail != null) message.setRecipient(adminEmail);
				}
				
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
					return serviceUser.modificar(userModified)
							.doOnNext(usr -> notifyAdminsOfUserUpdate(usr))
							.then();					
				}
				
				return Mono.empty();
			}
		default -> throw new IllegalArgumentException("Unexpected value: " + type);
		}
		
	}
	
	/**
	 * Send ALL non-admin users to the admin's output sink (initial contacts load).
	 * Completes once the list is sent — does NOT block the pipeline.
	 */
	private Mono<Void> sendAllUsersToAdmin(User adminUser, WebSocketSession session) {
		Sinks.Many<String> outputSink = getOutputSink(session);
		if (outputSink == null) return Mono.empty();

		return serviceUser.listar()
				.filter(usr -> !usr.getEmail().equals(adminUser.getEmail()) && !adminEmails.contains(usr.getEmail()))
				.doOnNext(usr -> {
					boolean isOnline = users.keySet().stream()
							.anyMatch(u -> u.getEmail() != null && u.getEmail().equals(usr.getEmail()));
					User userToSend = isOnline ? usr : usr.toBuilder().setStatus(false).build();
					outputSink.tryEmitNext(toJson(userToSend));
				})
				.then();
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
		    serviceUser.modificar(userModified)
		    		.doOnNext(usr -> notifyAdminsOfUserUpdate(usr))
		    		.subscribe();
		}
	}
	
	private String toJson(User user) {
		try {
			return mapper.writeValueAsString(user);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
	
	
}