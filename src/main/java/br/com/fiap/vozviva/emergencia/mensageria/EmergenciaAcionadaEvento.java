package br.com.fiap.vozviva.emergencia.mensageria;

/**
 * Mensagem publicada no RabbitMQ quando o cidadão aciona a emergência. Leva só o id: o consumidor
 * lê o estado atual no banco, o que evita processar dados desatualizados.
 */
public record EmergenciaAcionadaEvento(Long emergenciaId) {
}
