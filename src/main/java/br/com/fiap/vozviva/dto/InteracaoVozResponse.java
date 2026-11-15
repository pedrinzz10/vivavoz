package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.AcaoApp;
import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.model.Urgencia;

import java.time.Instant;

/** Uma interação do histórico do próprio usuário (também usada para avaliar a IA). */
public record InteracaoVozResponse(
		Long id,
		String texto,
		Intencao intencao,
		AcaoApp acao,
		Urgencia urgencia,
		Double confianca,
		int latenciaMs,
		String analisadoPor,
		Instant criadaEm) {
}
