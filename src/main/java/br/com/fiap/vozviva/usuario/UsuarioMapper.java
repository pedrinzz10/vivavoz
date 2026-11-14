package br.com.fiap.vozviva.usuario;

import br.com.fiap.vozviva.usuario.dto.UsuarioResponse;

final class UsuarioMapper {

	private UsuarioMapper() {
	}

	static UsuarioResponse paraResponse(Usuario usuario) {
		return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPapel());
	}

}
