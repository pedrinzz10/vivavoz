package br.com.fiap.vozviva.usuario.dto;

import br.com.fiap.vozviva.seguranca.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados para criar uma conta. A senha tem no máximo 72 caracteres, limite do BCrypt.
 */
public record CadastroUsuarioRequest(
		@NotBlank @Size(max = 120) String nome,
		@NotBlank @Email @Size(max = 160) String email,
		@NotBlank @Size(min = 8, max = 72)
		@Pattern(regexp = "^(?=.*[A-Za-zÀ-ÿ])(?=.*\\d).+$", message = "deve ter pelo menos uma letra e um número")
		String senha,
		@NotNull Papel papel) {

	@Override
	public String toString() {
		// Evita que a senha apareça em logs.
		return "CadastroUsuarioRequest[nome=" + nome + ", email=" + email + ", papel=" + papel + "]";
	}

}
