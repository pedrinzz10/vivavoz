package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.Papel;
import br.com.fiap.vozviva.validation.LimiteBytesUtf8;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados para criar uma conta. A senha tem no máximo 72 bytes em UTF-8, limite do BCrypt. O consentimento com os
 * termos de uso e privacidade é obrigatório (LGPD) e fica gravado com a data do aceite.
 */
public record CadastroUsuarioRequest(
		@NotBlank @Size(max = 120) String nome,
		@NotBlank @Email @Size(max = 160) String email,
		@NotBlank @Size(min = 8) @LimiteBytesUtf8(max = 72)
		@Pattern(regexp = CadastroUsuarioRequest.REGRA_SENHA, message = CadastroUsuarioRequest.MENSAGEM_SENHA)
		String senha,
		@NotNull Papel papel,
		@NotNull(message = "é preciso aceitar os termos de uso e privacidade")
		@AssertTrue(message = "é preciso aceitar os termos de uso e privacidade")
		Boolean consentimento) {

	/** Pelo menos uma letra e um número (a mesma regra vale para a troca de senha). */
	public static final String REGRA_SENHA = "^(?=.*[A-Za-zÀ-ÿ])(?=.*\\d).+$";
	public static final String MENSAGEM_SENHA = "deve ter pelo menos uma letra e um número";

	@Override
	public String toString() {
		// Evita que a senha apareça em logs.
		return "CadastroUsuarioRequest[nome=" + nome + ", email=" + email + ", papel=" + papel + "]";
	}

}
