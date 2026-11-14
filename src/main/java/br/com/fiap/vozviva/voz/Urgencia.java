package br.com.fiap.vozviva.voz;

public enum Urgencia {

	BAIXA,
	MEDIA,
	ALTA,
	CRITICA;

	static Urgencia maior(Urgencia a, Urgencia b) {
		if (a == null) {
			return b;
		}
		return b == null || a.compareTo(b) >= 0 ? a : b;
	}

}
