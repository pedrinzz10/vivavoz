package br.com.fiap.vozviva.mapper;

import br.com.fiap.vozviva.dto.UsuarioResponse;
import br.com.fiap.vozviva.model.Usuario;

public final class UsuarioMapper {

	private UsuarioMapper() {
	}

	public static UsuarioResponse paraResponse(Usuario usuario) {
		return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPapel(),
				usuario.getConsentimentoEm() == null ? null : usuario.getConsentimentoEm().toString(), usuario.getOrgao());
	}

}
