package br.com.fiap.vozviva.mapper;

import br.com.fiap.vozviva.dto.ContatoEmergenciaResponse;
import br.com.fiap.vozviva.dto.PerfilEmergenciaResponse.PessoaResumo;
import br.com.fiap.vozviva.dto.PerfilEmergenciaResponse;
import br.com.fiap.vozviva.model.ContatoEmergencia;
import br.com.fiap.vozviva.model.PerfilEmergencia;
import br.com.fiap.vozviva.model.Usuario;

public final class PerfilEmergenciaMapper {

	private PerfilEmergenciaMapper() {
	}

	public static PerfilEmergenciaResponse paraResponse(PerfilEmergencia perfil) {
		return new PerfilEmergenciaResponse(
				perfil.getId(),
				resumo(perfil.getCidadao()),
				resumo(perfil.getCuidador()),
				perfil.getMobilidade(),
				perfil.getCondicoesSaude(),
				perfil.getMedicamentos(),
				perfil.getObservacoes(),
				perfil.getCriadoEm().toString(),
				perfil.getAtualizadoEm().toString());
	}

	public static ContatoEmergenciaResponse paraResponse(ContatoEmergencia contato) {
		return new ContatoEmergenciaResponse(contato.getId(), contato.getNome(), contato.getTelefone(),
				contato.getParentesco(), contato.getPrioridade());
	}

	private static PessoaResumo resumo(Usuario usuario) {
		return new PessoaResumo(usuario.getId(), usuario.getNome());
	}

}
