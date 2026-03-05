package com.todoteg.message.impl;


import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.todoteg.message.IMessage;
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage implements IMessage {
    private EventType type = EventType.CHAT;
    @Id
    private String id;
    private String content;
    private String sender;
    private LocalDateTime date = LocalDateTime.now();
    private String recipient;
    
    private boolean isRead = false;
    
    
    
    
	public ChatMessage() {

	}
	public ChatMessage(EventType type, String content, String sender, boolean isRead) {
		this.type = type;
		this.content = content;
		this.sender = sender;
		this.isRead = isRead;
	}
	
	
	public static <T> Builder<T> builder() {
 		return new Builder<T>();
 	}
 	
 	public static class Builder<T> {
 		private EventType type;
 	    private String content;
 	    private String sender;
 	    private boolean isRead;
 		
 		private Builder() {}
 		
 		public Builder<T> setAll(ChatMessage message) {
			this.type = message.type;
			this.content = message.content;
			this.sender = message.sender;
			return this;
		} 		
 		
 		public Builder<T> setType(EventType type) {
			this.type = type;
			return this;
		}

		public Builder<T> setContent(String content) {
			this.content = content;
			return this;
		}

		public Builder<T> setSender(String sender) {
			this.sender = sender;
			return this;
		}
		
		public Builder<T> setRead(boolean isRead) {
			this.isRead = isRead;
			return this;
		}

		@Override
		public String toString() {
			return "Builder [type=" + type + ", content=" + content + ", sender=" + sender + "]";
		}


		public ChatMessage build() {
 			return new ChatMessage(type, content, sender, isRead);
 		}
 	}
	
	@Override
	public String toString() {
		return "Message [type=" + type + ", content=" + content + ", sender=" + sender + "]";
	}
	public String getId() {
		return id;
	}
	public EventType getType() {
		return type;
	}
	public void setType(EventType type) {
		this.type = type;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public String getSender() {
		return sender;
	}
	public void setSender(String sender) {
		this.sender = sender;
	}
	public LocalDateTime getDate() {
		return date;
	}
	public void setDate(LocalDateTime date) {
		this.date = date;
	}
	public String getRecipient() {
		return recipient;
	}
	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}
	public boolean isRead() {
		return isRead;
	}
	public void setRead(boolean isRead) {
		this.isRead = isRead;
	}
    
    
}