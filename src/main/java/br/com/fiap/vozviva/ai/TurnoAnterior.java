package br.com.fiap.vozviva.ai;

import br.com.fiap.vozviva.model.Intencao;

/**
 * Uma fala recente da mesma pessoa (do histórico de voz), para dar continuidade à conversa: depois de "quero me
 * aposentar", o "e quanto tempo demora?" é sobre a aposentadoria.
 */
public record TurnoAnterior(String fala, Intencao intencao) {
}
