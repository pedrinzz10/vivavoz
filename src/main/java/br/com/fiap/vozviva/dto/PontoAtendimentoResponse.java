package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.dto.AtendimentoProximoResponse.Rotas;
import br.com.fiap.vozviva.model.Acessibilidade;
import br.com.fiap.vozviva.model.TipoAtendimento;

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
