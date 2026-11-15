package br.com.fiap.vozviva.dto;

public record AvisoEmergenciaResponse(Long id, String contatoNome, String telefone, String mensagem, String enviadoEm) {
}
