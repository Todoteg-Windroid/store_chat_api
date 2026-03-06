package com.todoteg.repo;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.todoteg.message.impl.ChatMessage;

import reactor.core.publisher.Flux;

public interface IChatMessageRepo extends ReactiveMongoRepository<ChatMessage, String>{
	
	@Query("{ $or: [ { 'sender': ?0, 'recipient': ?1 }, { 'sender': ?1, 'recipient': ?0 } ] }")
	Flux<ChatMessage> findAllBySenderAndRecipient(String sender, String recipient);

	Flux<ChatMessage> findBySenderAndRecipientAndIsReadFalse(String sender, String remitente);

	/** Find all messages involving a given email (as sender or recipient) */
	@Query("{ $or: [ { 'sender': ?0 }, { 'recipient': ?0 } ] }")
	Flux<ChatMessage> findAllInvolvingEmail(String email);
}