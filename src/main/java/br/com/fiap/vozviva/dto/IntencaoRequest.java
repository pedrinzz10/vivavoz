package br.com.fiap.vozviva.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param texto        fala transcrita pelo app
 * @param localizacao  onde a pessoa está (opcional); usada só para já trazer os atendimentos próximos, nunca gravada
 * @param idioma       só português do Brasil é suportado
 */
public record IntencaoRequest(
		@NotBlank(message = "Fale ou digite o que você precisa.")
		@Size(max = 500, message = "A fala pode ter no máximo 500 caracteres.")
		String texto,
		@Valid LocalizacaoRequest localizacao,
		@Pattern(regexp = "(?i)pt(-br)?", message = "Por enquanto só entendemos português.")
		String idioma) {
}
