package br.com.fiap.vozviva.emergencia.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * Acionamento do botão de emergência. A localização é opcional (o GPS pode falhar), mas latitude e
 * longitude vêm sempre juntas.
 *
 * @param textoOriginal frase dita pelo cidadão, quando o acionamento veio por voz (ex.: "caí e não consigo levantar")
 */
public record AcionarEmergenciaRequest(
		@DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
		@DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
		@Size(max = 300) String textoOriginal) {
}
