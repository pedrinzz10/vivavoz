package br.com.fiap.vozviva.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank @Size(max = 160) String email,
		@NotBlank @Size(max = 72) String senha) {

	@Override
	public String toString() {
		// Evita que a senha apareça em logs.
		return "LoginRequest[email=" + email + "]";
	}

}
