package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.Mobilidade;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Criação do perfil de emergência.
 *
 * @param cidadaoEmail obrigatório só para o cuidador (identifica o cidadão). O cidadão cria o próprio perfil
 *                     sem informar o e-mail: vale o dono do token.
 * @param mobilidade   opcional (dado de saúde, LGPD): sem ele, fica {@code NAO_INFORMADO}
 */
public record PerfilEmergenciaRequest(
		@Email @Size(max = 160) String cidadaoEmail,
		Mobilidade mobilidade,
		@Size(max = 500) String condicoesSaude,
		@Size(max = 500) String medicamentos,
		@Size(max = 500) String observacoes) {
}
