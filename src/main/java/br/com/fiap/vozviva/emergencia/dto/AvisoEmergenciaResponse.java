package br.com.fiap.vozviva.emergencia.dto;

public record AvisoEmergenciaResponse(Long id, String contatoNome, String telefone, String mensagem, String enviadoEm) {
}
