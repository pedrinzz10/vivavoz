package br.com.fiap.vozviva.golpe;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras determinísticas que funcionam sem IA. Servem de piso de segurança: se a mensagem pede senha,
 * por exemplo, o resultado nunca sai como "sem sinais de risco", mesmo que o modelo erre. Também são o
 * plano B quando a IA está fora do ar.
 */
final class RegrasGolpe {

	enum Sinal {
		PEDIDO_DE_SENHA("Pede senha, código, token ou dados bancários, que nenhum órgão ou banco pede por mensagem",
				"\\bsenha\\b|\\bcodigo\\b.{0,30}\\b(recebeu|chegou|enviado|verificacao|seguranca|sms)|\\btoken\\b|\\bpin\\b|\\bcvv\\b"
						+ "|\\bdados bancarios\\b|\\b(numero|dados) do cartao\\b"),
		PEDIDO_DE_PAGAMENTO("Pede pagamento ou transferência (Pix, boleto, depósito)",
				"\\bpix\\b|\\btransfer[ei]|\\bboleto\\b|\\bdeposit|\\bpague\\b|\\bpagamento\\b|\\btaxa\\b"),
		URGENCIA("Cria urgência para você agir sem pensar",
				"\\burgente\\b|\\bimediat|\\b(ainda|somente|so|apenas) hoje\\b|\\bem ate \\d+|\\bultim[oa] (dia|aviso|chance)"
						+ "|\\b(sera|foi|esta) (bloquead|cancelad|suspens)|\\bbloquead"),
		LINK_SUSPEITO("Traz um link para clicar",
				"https?://|\\bwww\\.|\\bbit\\.ly\\b|\\btinyurl\\b|\\bcutt\\.ly\\b|\\bclique (no|aqui|neste)"),
		NUMERO_NOVO("Diz ser um parente com número novo",
				"\\b(troquei|mudei) de (numero|celular|telefone)|\\bmeu (numero|celular) novo\\b|\\beste e meu novo"
						+ "|\\bperdi (o|meu) (celular|telefone)\\b|\\bcelular (quebrou|foi roubado)\\b|\\bnumero (temporario|provisorio)\\b"),
		PREMIO("Promete prêmio, sorteio ou dinheiro fácil",
				"\\bganhou\\b|\\bsortead|\\bpremio\\b|\\bresgat"),
		ORGAO_OU_BENEFICIO("Usa o nome de um órgão, banco ou benefício para parecer oficial",
				"\\binss\\b|\\baposentadoria\\b|\\bbeneficio\\b|\\breceita federal\\b|\\bfgts\\b|\\bcentral (do|de) (banco|cartao|seguranca)");

		private final String descricao;
		private final Pattern padrao;

		Sinal(String descricao, String regex) {
			this.descricao = descricao;
			this.padrao = Pattern.compile(regex);
		}

		String descricao() {
			return descricao;
		}
	}

	private RegrasGolpe() {
	}

	static Resultado analisar(String texto) {
		String normalizado = normalizar(texto);
		Set<Sinal> sinais = EnumSet.noneOf(Sinal.class);
		for (Sinal sinal : Sinal.values()) {
			if (sinal.padrao.matcher(normalizado).find()) {
				sinais.add(sinal);
			}
		}
		return new Resultado(sinais, piso(sinais), tipoProvavel(sinais));
	}

	private static ClassificacaoGolpe piso(Set<Sinal> sinais) {
		boolean pagamentoComPressao = sinais.contains(Sinal.PEDIDO_DE_PAGAMENTO)
				&& (sinais.contains(Sinal.URGENCIA) || sinais.contains(Sinal.NUMERO_NOVO));
		boolean linkComIsca = sinais.contains(Sinal.LINK_SUSPEITO) && (sinais.contains(Sinal.URGENCIA)
				|| sinais.contains(Sinal.PREMIO) || sinais.contains(Sinal.ORGAO_OU_BENEFICIO));
		if (sinais.contains(Sinal.PEDIDO_DE_SENHA) || pagamentoComPressao || linkComIsca) {
			return ClassificacaoGolpe.PROVAVEL_GOLPE;
		}
		if (sinais.contains(Sinal.NUMERO_NOVO) || sinais.contains(Sinal.PREMIO) || sinais.size() >= 2) {
			return ClassificacaoGolpe.SUSPEITA;
		}
		return null;
	}

	private static TipoGolpe tipoProvavel(Set<Sinal> sinais) {
		if (sinais.contains(Sinal.NUMERO_NOVO)) {
			return TipoGolpe.FALSO_PARENTE;
		}
		if (sinais.contains(Sinal.PREMIO)) {
			return TipoGolpe.PREMIO_FALSO;
		}
		if (sinais.contains(Sinal.ORGAO_OU_BENEFICIO)) {
			return TipoGolpe.FALSO_BENEFICIO;
		}
		if (sinais.contains(Sinal.LINK_SUSPEITO)) {
			return TipoGolpe.LINK_MALICIOSO;
		}
		if (sinais.contains(Sinal.PEDIDO_DE_PAGAMENTO)) {
			return TipoGolpe.FALSA_COBRANCA;
		}
		return sinais.isEmpty() ? TipoGolpe.NENHUM : TipoGolpe.OUTRO;
	}

	/** Minúsculas e sem acentos, para "Benefício" e "beneficio" casarem com a mesma regra. */
	static String normalizar(String texto) {
		String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return semAcento.toLowerCase(Locale.ROOT);
	}

	/**
	 * @param piso classificação mínima garantida pelas regras ({@code null} se nenhuma regra disparou)
	 */
	record Resultado(Set<Sinal> sinais, ClassificacaoGolpe piso, TipoGolpe tipoProvavel) {

		List<String> descricoes() {
			List<String> descricoes = new ArrayList<>();
			sinais.forEach(s -> descricoes.add(s.descricao()));
			return descricoes;
		}

	}

}
