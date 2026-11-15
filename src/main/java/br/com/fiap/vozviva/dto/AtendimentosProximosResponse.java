package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.TipoAtendimento;

import java.util.List;

/**
 * Resultado de "o mais perto de mim", do mais próximo para o mais distante.
 *
 * @param semAcessivelNoRaio   {@code true} quando foi pedido local acessível, nenhum tinha acessibilidade
 *                             confirmada e a lista traz os de acessibilidade não informada
 * @param mensagemFalada       frase curta para o app ler em voz alta
 * @param orientacaoEmergencia orientação de ligar 192 (só para hospital e UPA)
 * @param telefoneEmergencia   {@code 192} nas buscas por hospital e UPA
 * @param observacao           limitação conhecida (a rota do app de navegação pode não ser acessível)
 * @param creditoDados         crédito exigido pela licença ODbL do OpenStreetMap
 */
public record AtendimentosProximosResponse(
		TipoAtendimento tipo,
		List<AtendimentoProximoResponse> atendimentos,
		boolean semAcessivelNoRaio,
		String mensagemFalada,
		String orientacaoEmergencia,
		String telefoneEmergencia,
		String observacao,
		String creditoDados) {
}
