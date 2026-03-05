package com.todoteg.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Service;

import com.todoteg.models.User;
import com.todoteg.repo.IUserRepo;
import com.todoteg.service.IUserService;

import reactor.core.publisher.Mono;

@Service
public class UserServiceImpl extends CRUDImpl<User, String> implements IUserService{

	@Autowired
	private IUserRepo repo;
	
	@Override
	protected ReactiveMongoRepository<User, String> getRepo() {
		// TODO Auto-generated method stub
		return repo;
	}

	@Override
	public Mono<User> findByName(String name) {
		return repo.findByName(name);
	}

	@Override
	public Mono<User> findByEmail(String email) {
		return repo.findByEmail(email);
	}
	
	@Override
	public Mono<User> modificar(User user) {
		return repo.findByEmail(user.getEmail())
				.switchIfEmpty(repo.findByName(user.getName()))
				.zipWith(Mono.just(user), (userDB, userParam) -> userDB.toBuilder()
						.setStatus(user.getStatus())
						.setName(user.getName() != null ? user.getName() : userDB.getName())
						.setEmail(user.getEmail() != null ? user.getEmail() : userDB.getEmail())
						.setStoreUserId(user.getStoreUserId() != null ? user.getStoreUserId() : userDB.getStoreUserId())
						.build())
				.flatMap(repo::save);
	}

}
