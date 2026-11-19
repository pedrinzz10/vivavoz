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
 * @param atendimentos   em BUSCAR_ATENDIMENTO com localização, os locais mais próximos já encontrados (também em
 *                       TIRAR_DUVIDA, quando o serviço tem atendimento presencial, como o CRAS)
 * @param orientacao     em TIRAR_DUVIDA, a orientação oficial (passos, link e telefone) da base curada
 */
public record IntencaoResponse(
		Intencao intencao,
		AcaoApp acao,
		Urgencia urgencia,
		EntidadesFala entidades,
		String mensagemFalada,
		double confianca,
		String analisadoPor,
		AtendimentosProximosResponse atendimentos,
		OrientacaoServicoResponse orientacao) {
}
