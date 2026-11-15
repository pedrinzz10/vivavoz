package br.com.fiap.vozviva.util;

import java.util.regex.Pattern;

/**
 * Telefones brasileiros: grava no padrão internacional (+55 + DDD + número), que o app usa direto
 * para ligar com um toque. O formato de entrada é validado no DTO (ContatoEmergenciaRequest).
 */
public final class Telefones {

	private static final Pattern NAO_DIGITO = Pattern.compile("\\D");

	private Telefones() {
	}

	public static String normalizar(String telefone) {
		String digitos = NAO_DIGITO.matcher(telefone).replaceAll("");
		if (digitos.length() == 10 || digitos.length() == 11) {
			digitos = "55" + digitos;
		}
		return "+" + digitos;
	}

}
