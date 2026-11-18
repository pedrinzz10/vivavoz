package br.com.fiap.vozviva.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados da conta que o próprio usuário pode corrigir (LGPD, art. 18, III). O papel não muda por aqui, e a troca de
 * e-mail fica de fora: sem confirmar o endereço novo, quem tivesse o token conseguiria tomar a conta.
 */
public record AtualizarContaRequest(@NotBlank @Size(max = 120) String nome) {
}
