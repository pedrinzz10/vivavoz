package br.com.fiap.vozviva.emergencia.dto;

import br.com.fiap.vozviva.emergencia.StatusEmergencia;

/**
 * @param mensagemFalada frase para o app ler em voz alta ao cidadão
 */
public record EmergenciaResponse(
		Long id,
		Long perfilId,
		String cidadaoNome,
		StatusEmergencia status,
		Double latitude,
		Double longitude,
		String textoOriginal,
		Integer contatosNotificados,
		String acionadaEm,
		String notificadaEm,
		String encerradaEm,
		String mensagemFalada) {
}
