package br.com.fiap.vozviva.model;

/**
 * Resultado da verificação, do menor para o maior risco. A ordem importa: ao combinar a IA com as
 * regras determinísticas, vale sempre a classificação de maior risco.
 */
public enum ClassificacaoGolpe {

	SEM_SINAIS_DE_RISCO(0),
	NAO_E_POSSIVEL_AVALIAR(1),
	SUSPEITA(2),
	PROVAVEL_GOLPE(3);

	private final int risco;

	ClassificacaoGolpe(int risco) {
		this.risco = risco;
	}

	public static ClassificacaoGolpe maiorRisco(ClassificacaoGolpe a, ClassificacaoGolpe b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return a.risco >= b.risco ? a : b;
	}

	public boolean indicaRisco() {
		return this == SUSPEITA || this == PROVAVEL_GOLPE;
	}

}
