package com.todoteg.message;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.todoteg.message.impl.ChatMessage;
import com.todoteg.message.impl.EventType;
import com.todoteg.message.impl.JoinChatMessage;
import com.todoteg.message.impl.JoinMessage;
import com.todoteg.message.impl.LeaveChatMessage;
import com.todoteg.message.impl.LeaveMessage;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "messages")
@JsonTypeInfo(use = NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = JoinMessage.class, name = "JOIN"),
        @JsonSubTypes.Type(value = JoinChatMessage.class, name = "JOIN_CHAT"),
        @JsonSubTypes.Type(value = LeaveChatMessage.class, name = "LEAVE_CHAT"),
        @JsonSubTypes.Type(value = LeaveMessage.class, name = "LEAVE"),
        @JsonSubTypes.Type(value = ChatMessage.class, name = "CHAT")
})
public interface IMessage {

	EventType getType();
	String getSender();
}
