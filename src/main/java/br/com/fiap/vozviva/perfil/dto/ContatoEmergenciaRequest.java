package br.com.fiap.vozviva.perfil.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contato avisado na emergência. Prioridade 1 é o primeiro a ser avisado.
 */
public record ContatoEmergenciaRequest(
		@NotBlank @Size(max = 120) String nome,
		@NotBlank
		@Pattern(regexp = "^(\\+?55[\\s-]?)?\\(?[1-9]\\d\\)?[\\s-]?9?\\d{4}[\\s-]?\\d{4}$",
				message = "deve ser um telefone brasileiro com DDD, ex.: (11) 91234-5678")
		String telefone,
		@Size(max = 60) String parentesco,
		@NotNull @Min(1) @Max(10) Integer prioridade) {
}
