package br.com.fiap.vozviva.mapper;

import br.com.fiap.vozviva.dto.ContatoEmergenciaResponse;
import br.com.fiap.vozviva.dto.PerfilEmergenciaResponse;
import br.com.fiap.vozviva.dto.PerfilEmergenciaResponse.PessoaResumo;
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

	/** {@code null} quando não há a pessoa (ex.: perfil mantido pelo próprio cidadão, sem cuidador). */
	private static PessoaResumo resumo(Usuario usuario) {
		return usuario == null ? null : new PessoaResumo(usuario.getId(), usuario.getNome());
	}

}
