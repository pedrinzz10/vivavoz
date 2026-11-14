package br.com.fiap.vozviva.perfil;

import br.com.fiap.vozviva.perfil.dto.ContatoEmergenciaResponse;
import br.com.fiap.vozviva.perfil.dto.PerfilEmergenciaResponse;
import br.com.fiap.vozviva.perfil.dto.PerfilEmergenciaResponse.PessoaResumo;
import br.com.fiap.vozviva.usuario.Usuario;

final class PerfilEmergenciaMapper {

	private PerfilEmergenciaMapper() {
	}

	static PerfilEmergenciaResponse paraResponse(PerfilEmergencia perfil) {
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

	static ContatoEmergenciaResponse paraResponse(ContatoEmergencia contato) {
		return new ContatoEmergenciaResponse(contato.getId(), contato.getNome(), contato.getTelefone(),
				contato.getParentesco(), contato.getPrioridade());
	}

	private static PessoaResumo resumo(Usuario usuario) {
		return new PessoaResumo(usuario.getId(), usuario.getNome());
	}

}
