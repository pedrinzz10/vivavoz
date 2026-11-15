package br.com.fiap.vozviva.atendimento;

import br.com.fiap.vozviva.atendimento.dto.AtendimentoProximoResponse.Rotas;

import java.util.Locale;

/**
 * Links que abrem a rota até o destino no app de navegação do celular. Não exigem chave de API.
 * Coordenadas sempre com ponto decimal, independente do idioma do servidor.
 */
final class LinksRota {

	private LinksRota() {
	}

	static Rotas para(double latitude, double longitude) {
		String lat = formatar(latitude);
		String lng = formatar(longitude);
		return new Rotas(
				"https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lng,
				"https://waze.com/ul?ll=" + lat + "," + lng + "&navigate=yes",
				"geo:" + lat + "," + lng);
	}

	private static String formatar(double coordenada) {
		return String.format(Locale.ROOT, "%.6f", coordenada);
	}

}
