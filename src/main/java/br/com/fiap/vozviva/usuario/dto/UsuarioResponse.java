package br.com.fiap.vozviva.usuario.dto;

import br.com.fiap.vozviva.seguranca.Papel;

public record UsuarioResponse(Long id, String nome, String email, Papel papel) {
}
