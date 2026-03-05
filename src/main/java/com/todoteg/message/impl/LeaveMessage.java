package com.todoteg.message.impl;


import com.todoteg.message.IMessage;

public class LeaveMessage implements IMessage{
    private EventType type= EventType.LEAVE;
    private String sender;
    
    
	public LeaveMessage() {

	}
	public LeaveMessage(EventType type,  String sender) {
		this.type = type;
		this.sender = sender;
	}
	
	
	public static <T> Builder<T> builder() {
 		return new Builder<T>();
 	}
 	
 	public static class Builder<T> {
 		private EventType type =EventType.LEAVE;
 	    private String sender;
 		
 		private Builder() {}
 		
 		public Builder<T> setAll(LeaveMessage event) {
			this.type = event.type;
			this.sender = event.sender;
			return this;
		} 		
 		
 		public Builder<T> setType(EventType type) {
			this.type = type;
			return this;
		}

		public Builder<T> setSender(String sender) {
			this.sender = sender;
			return this;
		}

		@Override
		public String toString() {
			return "Builder [type=" + type +  ", sender=" + sender + "]";
		}


		public LeaveMessage build() {
 			return new LeaveMessage(type,  sender);
 		}
 	}
	
	@Override
	public String toString() {
		return "Event [type=" + type +  ", sender=" + sender + "]";
	}
	public EventType getType() {
		return type;
	}
	public void setType(EventType type) {
		this.type = type;
	}
	public String getSender() {
		return sender;
	}
	public void setSender(String sender) {
		this.sender = sender;
	}
    
    
}