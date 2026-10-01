package com.conflux.auth;

import com.conflux.user.ValidUsername;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @ValidUsername String username,
		@NotNull @ValidPassword String password,
		@NotBlank @Size(max = 100) String displayName) {

	@Override
	public String toString() {
		return "RegisterRequest[email=" + this.email + ", username=" + this.username + ", password=****, displayName="
				+ this.displayName + "]";
	}

}
