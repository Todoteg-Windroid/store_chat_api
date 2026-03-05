package com.todoteg.service;

import com.todoteg.models.User;

import reactor.core.publisher.Mono;

public interface IUserService extends ICRUD<User, String>{
	Mono<User> findByName(String name);
	Mono<User> findByEmail(String email);
}
