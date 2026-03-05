package com.todoteg.repo;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.todoteg.models.User;

import reactor.core.publisher.Mono;

public interface IUserRepo extends ReactiveMongoRepository<User, String>{
	Mono<User> findByName(String name);
	Mono<User> findByEmail(String email);
}
