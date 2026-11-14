package br.com.fiap.vozviva.emergencia;

/**
 * Ciclo de vida: ACIONADA → NOTIFICADA → ATENDIDA. O cidadão pode cancelar enquanto a emergência
 * estiver aberta (ACIONADA ou NOTIFICADA).
 */
public enum StatusEmergencia {
	ACIONADA,
	NOTIFICADA,
	ATENDIDA,
	CANCELADA;

	public boolean aberta() {
		return this == ACIONADA || this == NOTIFICADA;
	}
}
