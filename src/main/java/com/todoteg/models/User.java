package com.todoteg.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "users")
public class User {
	@Id
	private String id;
	private String name;
	private String email;
	private Long storeUserId;
	private Boolean status;
	
	public String getName() {
		return name;
	}
	public String getEmail() {
		return email;
	}
	public Long getStoreUserId() {
		return storeUserId;
	}
	public Boolean getStatus() {
		return status;
	}
	
	@Override
	public String toString() {
		return "User [name=" + name + ", email=" + email + ", status=" + status + "]";
	}
	
	public static Builder builder() {
 		return new Builder();
 	}
	
	public Builder toBuilder(){
		return new Builder(this);
	}
	
	public static class Builder {
 		private User user;
 		
 		private Builder() {
 			user = new User();
 		}
 		
 		private Builder(User user) {
 			this.user = user;
 		}

 		public Builder setName(String name) {
 			this.user.name = name;
			return this;
 		}
 		public Builder setEmail(String email) {
 			this.user.email = email;
			return this;
 		}
 		public Builder setStoreUserId(Long storeUserId) {
 			this.user.storeUserId = storeUserId;
			return this;
 		}
 		public Builder setStatus(Boolean status) {
 			this.user.status = status;
			return this;
 		}

		@Override
		public String toString() {
			return "Builder [user=" + user + "]";
		}

		public User build() {
 			return user;
 		}
 	}
	
}
