package br.com.fiap.vozviva.atendimento.dto;

import br.com.fiap.vozviva.atendimento.Acessibilidade;
import br.com.fiap.vozviva.atendimento.TipoAtendimento;
import br.com.fiap.vozviva.atendimento.dto.AtendimentoProximoResponse.Rotas;

import java.time.Instant;

/** Local de atendimento para listagem e detalhe. */
public record PontoAtendimentoResponse(
		Long id,
		String nome,
		TipoAtendimento tipo,
		String endereco,
		String telefone,
		double latitude,
		double longitude,
		Acessibilidade acessibilidade,
		Boolean banheiroAdaptado,
		Boolean funciona24h,
		String fonte,
		Instant atualizadoEm,
		Rotas rotas) {
}
