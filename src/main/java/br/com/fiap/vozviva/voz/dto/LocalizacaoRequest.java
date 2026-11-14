package br.com.fiap.vozviva.voz.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record LocalizacaoRequest(
		@NotNull(message = "Informe a latitude.")
		@DecimalMin(value = "-90", message = "Latitude deve estar entre -90 e 90.")
		@DecimalMax(value = "90", message = "Latitude deve estar entre -90 e 90.")
		Double latitude,
		@NotNull(message = "Informe a longitude.")
		@DecimalMin(value = "-180", message = "Longitude deve estar entre -180 e 180.")
		@DecimalMax(value = "180", message = "Longitude deve estar entre -180 e 180.")
		Double longitude) {
}
