package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.validation.LimiteBytesUtf8;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Troca de senha pelo próprio usuário: exige a senha atual e a nova segue as mesmas regras do cadastro.
 */
public record TrocarSenhaRequest(
		@NotBlank @LimiteBytesUtf8(max = 72) String senhaAtual,
		@NotBlank @Size(min = 8) @LimiteBytesUtf8(max = 72)
		@Pattern(regexp = CadastroUsuarioRequest.REGRA_SENHA, message = CadastroUsuarioRequest.MENSAGEM_SENHA)
		String novaSenha) {

	@Override
	public String toString() {
		// Evita que as senhas apareçam em logs.
		return "TrocarSenhaRequest[***]";
	}

}
