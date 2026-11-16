package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.Papel;

/**
 * @param consentimentoEm quando a pessoa aceitou os termos de uso e privacidade (ISO-8601; {@code null} em contas
 *                        criadas antes do consentimento existir)
 */
public record UsuarioResponse(Long id, String nome, String email, Papel papel, String consentimentoEm) {
}
