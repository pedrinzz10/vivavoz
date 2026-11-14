package br.com.fiap.vozviva.perfil.dto;

import br.com.fiap.vozviva.perfil.Mobilidade;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Atualização do perfil. O cidadão do perfil não muda depois de criado.
 */
public record AtualizarPerfilEmergenciaRequest(
		@NotNull Mobilidade mobilidade,
		@Size(max = 500) String condicoesSaude,
		@Size(max = 500) String medicamentos,
		@Size(max = 500) String observacoes) {
}
