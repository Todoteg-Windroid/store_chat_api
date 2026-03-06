package com.todoteg.controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.todoteg.models.User;
import com.todoteg.repo.IChatMessageRepo;
import com.todoteg.service.IUserService;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ChatRestController {

    private final IChatMessageRepo chatMessageRepo;
    private final IUserService userService;
    private final List<String> adminEmails;

    public ChatRestController(IChatMessageRepo chatMessageRepo, IUserService userService,
            @Value("${chat.admin.emails}") String adminEmailsStr) {
        this.chatMessageRepo = chatMessageRepo;
        this.userService = userService;
        this.adminEmails = List.of(adminEmailsStr.split(","));
    }

    /**
     * GET /api/contacts — returns all clients that have chat history with the admin.
     * Only accessible by admin users.
     */
    @GetMapping("/contacts")
    public Flux<User> getContacts(ServerWebExchange exchange) {
        String email = (String) exchange.getAttribute("auth_email");
        if (email == null || !adminEmails.contains(email.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }

        return chatMessageRepo.findAllInvolvingEmail(email)
                .map(msg -> msg.getSender().equals(email) ? msg.getRecipient() : msg.getSender())
                .distinct()
                .filter(e -> e != null && !adminEmails.contains(e))
                .flatMap(userService::findByEmail)
                .map(user -> user.toBuilder().setStatus(false).build());
    }
}
