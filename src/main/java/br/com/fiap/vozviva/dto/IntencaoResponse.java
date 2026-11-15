package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.AcaoApp;
import br.com.fiap.vozviva.model.EntidadesFala;
import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.model.Urgencia;

/**
 * Comando para o app: qual tela abrir e com que dados. O contrato é o mesmo mesmo quando a IA falha.
 *
 * @param acao           tela ou fluxo que o app abre (decidida pela intenção)
 * @param mensagemFalada frase curta (até 200 caracteres) para o app ler em voz alta
 * @param confianca      de 0 a 1
 * @param analisadoPor   {@code IA_E_REGRAS} ou {@code REGRAS} (sem IA disponível)
 * @param atendimentos   em BUSCAR_ATENDIMENTO com localização, os locais mais próximos já encontrados
 */
public record IntencaoResponse(
		Intencao intencao,
		AcaoApp acao,
		Urgencia urgencia,
		EntidadesFala entidades,
		String mensagemFalada,
		double confianca,
		String analisadoPor,
		AtendimentosProximosResponse atendimentos) {
}
