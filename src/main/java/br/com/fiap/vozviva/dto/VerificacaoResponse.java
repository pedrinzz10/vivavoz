package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.ClassificacaoGolpe;
import br.com.fiap.vozviva.model.TipoGolpe;

import java.util.List;

/**
 * @param mensagemFalada frase curta (até 200 caracteres) para o app ler em voz alta
 * @param fontes         orientações oficiais usadas na análise (transparência sobre de onde veio a resposta)
 * @param analisadoPor   {@code IA_E_REGRAS} ou {@code REGRAS} (quando a IA estava indisponível)
 * @param confianca      confiança da IA, de 0 a 1 ({@code null} quando só as regras analisaram)
 */
public record VerificacaoResponse(
		ClassificacaoGolpe classificacao,
		TipoGolpe tipoGolpe,
		List<String> sinais,
		String orientacao,
		String canalOficial,
		String mensagemFalada,
		List<Fonte> fontes,
		String analisadoPor,
		Double confianca) {

	public record Fonte(String titulo, String orgao, String url) {
	}

}
