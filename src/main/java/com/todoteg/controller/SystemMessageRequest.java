package com.todoteg.controller;

/**
 * Cuerpo del mensaje de sistema que otro servicio (store-api) inyecta:
 * un mensaje de la tienda hacia un comprador para coordinar la entrega.
 */
public class SystemMessageRequest {
    private String recipient;
    private String content;

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
