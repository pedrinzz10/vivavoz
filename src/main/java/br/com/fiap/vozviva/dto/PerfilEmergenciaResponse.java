package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.Mobilidade;

public record PerfilEmergenciaResponse(
		Long id,
		PessoaResumo cidadao,
		PessoaResumo cuidador,
		Mobilidade mobilidade,
		String condicoesSaude,
		String medicamentos,
		String observacoes,
		String criadoEm,
		String atualizadoEm) {

	public record PessoaResumo(Long id, String nome) {
	}

}
