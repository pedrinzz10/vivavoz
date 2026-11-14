package br.com.fiap.vozviva.perfil.dto;

/**
 * @param telefone no padrão internacional (+55...), pronto para o app ligar
 */
public record ContatoEmergenciaResponse(Long id, String nome, String telefone, String parentesco, Integer prioridade) {
}
