package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.Acessibilidade;
import br.com.fiap.vozviva.model.TipoAtendimento;

/**
 * Um atendimento encontrado perto do usuário, com a rota pronta para abrir no celular.
 *
 * @param distanciaMetros distância em linha reta até o local
 * @param aviso           alerta sobre a acessibilidade (ex.: não informada, ligue antes); {@code null} quando confirmada
 * @param rotas           links para Google Maps, Waze ou qualquer app que entenda {@code geo:}
 */
public record AtendimentoProximoResponse(
		Long id,
		String nome,
		TipoAtendimento tipo,
		String endereco,
		String telefone,
		double latitude,
		double longitude,
		long distanciaMetros,
		Acessibilidade acessibilidade,
		Boolean banheiroAdaptado,
		Boolean funciona24h,
		String aviso,
		Rotas rotas) {

	public record Rotas(String googleMaps, String waze, String geo) {
	}

}
