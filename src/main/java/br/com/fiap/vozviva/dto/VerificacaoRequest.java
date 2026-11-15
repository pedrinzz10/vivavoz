package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.CanalMensagem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param texto mensagem recebida, colada ou ditada pelo cidadão (nunca é gravada)
 * @param canal por onde a mensagem chegou; opcional
 */
public record VerificacaoRequest(
		@NotBlank @Size(max = 2000) String texto,
		CanalMensagem canal) {

	@Override
	public String toString() {
		// O texto pode ter dados pessoais: não aparece em logs.
		return "VerificacaoRequest[canal=" + canal + ", tamanho=" + (texto == null ? 0 : texto.length()) + "]";
	}

}
