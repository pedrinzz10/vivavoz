package br.com.fiap.vozviva.perfil.dto;

import br.com.fiap.vozviva.perfil.Mobilidade;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Criação do perfil pelo cuidador. O cidadão é identificado pelo e-mail da conta dele.
 */
public record PerfilEmergenciaRequest(
		@NotBlank @Email @Size(max = 160) String cidadaoEmail,
		@NotNull Mobilidade mobilidade,
		@Size(max = 500) String condicoesSaude,
		@Size(max = 500) String medicamentos,
		@Size(max = 500) String observacoes) {
}
