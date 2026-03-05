package com.todoteg.message.impl;

import com.todoteg.message.IMessage;

public class JoinMessage implements IMessage{
    private EventType type= EventType.JOIN;
    private String username;
    
	public JoinMessage() {

	}
	public JoinMessage(EventType type,  String sender) {
		this.type = type;
		this.username = sender;
	}
	
	
	public static <T> Builder<T> builder() {
 		return new Builder<T>();
 	}
 	
 	public static class Builder<T> {
 		private EventType type;
 	    private String username;
 		
 		private Builder() {}
 		
 		public Builder<T> setAll(JoinMessage event) {
			this.type = event.type;
			this.username = event.username;
			return this;
		} 		
 		
 		public Builder<T> setType(EventType type) {
			this.type = type;
			return this;
		}

		public Builder<T> setUsername(String sender) {
			this.username = sender;
			return this;
		}

		@Override
		public String toString() {
			return "Builder [type=" + type +  ", username=" + username + "]";
		}


		public JoinMessage build() {
 			return new JoinMessage(type,  username);
 		}
 	}
	
	@Override
	public String toString() {
		return "Event [type=" + type +  ", username=" + username + "]";
	}
	public EventType getType() {
		return type;
	}
	public void setType(EventType type) {
		this.type = type;
	}
	public String getSender() {
		return username;
	}
	public void setUsername(String sender) {
		this.username = sender;
	}
    
}