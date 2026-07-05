package com.todoteg.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.todoteg.message.impl.ChatMessage;
import com.todoteg.message.impl.EventType;
import com.todoteg.messaging.MessagingService;

import reactor.core.publisher.Mono;

/**
 * Endpoint interno servicio-a-servicio (lo llama store-api, NO el navegador).
 * Vive fuera de /api/ para saltar el filtro de JWT del chat; su seguridad es
 * un token interno compartido por configuración. Inserta un mensaje de la
 * tienda hacia un comprador (mismo canal que el chat normal: Mongo + WebSocket).
 */
@RestController
public class InternalMessageController {

    private final MessagingService messagingService;
    private final String internalToken;
    private final String adminEmail;

    public InternalMessageController(
            MessagingService messagingService,
            @Value("${chat.internal.token:}") String internalToken,
            @Value("${chat.admin.emails}") String adminEmails) {
        this.messagingService = messagingService;
        this.internalToken = internalToken;
        // La tienda es el primer admin configurado; es el emisor del mensaje.
        this.adminEmail = adminEmails.split(",")[0].trim();
    }

    @PostMapping("/internal/system-message")
    public Mono<ResponseEntity<Void>> systemMessage(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody SystemMessageRequest body) {

        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(token)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        if (body.getRecipient() == null || body.getContent() == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        ChatMessage message = new ChatMessage();
        message.setType(EventType.CHAT);
        message.setSender(adminEmail);
        message.setRecipient(body.getRecipient());
        message.setContent(body.getContent());
        message.setRead(false);

        // Guarda en Mongo y empuja por WebSocket si el comprador está conectado.
        return messagingService.onNext(message, adminEmail, body.getRecipient())
                .thenReturn(ResponseEntity.<Void>ok().build());
    }
}
