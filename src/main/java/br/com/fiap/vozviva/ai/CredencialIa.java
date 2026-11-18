package br.com.fiap.vozviva.ai;

/**
 * Diz se há credencial para chamar o modelo de IA: a API key do OCI Generative AI ou a assinatura completa com
 * o usuário da OCI. Sem nenhuma das duas, o modelo não é chamado e as regras respondem sozinhas.
 *
 * @param configurada há API key ou assinatura configurada
 */
public record CredencialIa(boolean configurada) {

}
