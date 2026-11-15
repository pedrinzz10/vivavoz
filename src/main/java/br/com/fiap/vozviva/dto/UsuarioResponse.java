package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.Papel;

public record UsuarioResponse(Long id, String nome, String email, Papel papel) {
}
