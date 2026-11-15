package br.com.fiap.vozviva.model;

/**
 * Ciclo de vida: ACIONADA → NOTIFICADA → ATENDIDA. O cidadão pode cancelar enquanto a emergência
 * estiver aberta. FALHA_NO_AVISO: esgotados os reenvios, os contatos não puderam ser avisados
 * (o app orienta a ligar 192); continua aberta para ser atendida ou cancelada.
 */
public enum StatusEmergencia {
	ACIONADA,
	NOTIFICADA,
	FALHA_NO_AVISO,
	ATENDIDA,
	CANCELADA;

	public boolean aberta() {
		return this == ACIONADA || this == NOTIFICADA || this == FALHA_NO_AVISO;
	}
}
