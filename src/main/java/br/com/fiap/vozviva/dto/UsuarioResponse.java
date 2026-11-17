package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.AreaOrgao;
import br.com.fiap.vozviva.model.Papel;

/**
 * @param consentimentoEm quando a pessoa aceitou os termos de uso e privacidade (ISO-8601; {@code null} em contas
 *                        criadas antes do consentimento existir)
 * @param orgao           órgão atendido pelo gestor; {@code null} para cidadão e cuidador
 */
public record UsuarioResponse(Long id, String nome, String email, Papel papel, String consentimentoEm,
		AreaOrgao orgao) {
}
