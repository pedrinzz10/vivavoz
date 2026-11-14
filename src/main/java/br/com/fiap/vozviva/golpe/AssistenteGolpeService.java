package br.com.fiap.vozviva.golpe;

import br.com.fiap.vozviva.golpe.AnaliseIaGolpe.RespostaIa;
import br.com.fiap.vozviva.golpe.BaseConhecimentoGolpes.DocumentoConhecimento;
import br.com.fiap.vozviva.golpe.dto.EstatisticaGolpeResponse;
import br.com.fiap.vozviva.golpe.dto.VerificacaoRequest;
import br.com.fiap.vozviva.golpe.dto.VerificacaoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Assistente contra golpes: combina três camadas.
 * <ol>
 *   <li><b>Recuperação (RAG):</b> busca na base curada as orientações oficiais mais parecidas com a mensagem;</li>
 *   <li><b>IA generativa:</b> o modelo analisa a mensagem com essas orientações e devolve a análise estruturada;</li>
 *   <li><b>Regras determinísticas:</b> garantem um piso de segurança (ex.: pedido de senha nunca sai como
 *       "sem risco") e respondem sozinhas se a IA estiver indisponível.</li>
 * </ol>
 */
@Service
public class AssistenteGolpeService {

	static final int MAXIMO_MENSAGEM_FALADA = 200;
	private static final int ORIENTACOES_RECUPERADAS = 3;
	private static final int MAXIMO_SINAIS = 6;

	private static final Map<ClassificacaoGolpe, String> FRASES_PADRAO = Map.of(
			ClassificacaoGolpe.PROVAVEL_GOLPE,
			"Isso parece golpe. Não clique, não pague e não passe senha. Confirme pelo canal oficial.",
			ClassificacaoGolpe.SUSPEITA,
			"Essa mensagem tem sinais de golpe. Antes de fazer qualquer coisa, confirme pelo canal oficial.",
			ClassificacaoGolpe.SEM_SINAIS_DE_RISCO,
			"Não encontrei sinais de golpe. Mesmo assim, na dúvida, confirme pelo canal oficial.",
			ClassificacaoGolpe.NAO_E_POSSIVEL_AVALIAR,
			"Não consegui avaliar essa mensagem. Na dúvida, não clique, não pague e peça ajuda a alguém de confiança.");

	private static final String ORIENTACAO_PADRAO =
			"Não clique em links, não faça pagamentos e não informe senhas. Confirme pelo canal oficial que você já conhece.";

	private final BaseConhecimentoGolpes base;
	private final AnaliseIaGolpe ia;
	private final VerificacaoGolpeRepository verificacoes;

	public AssistenteGolpeService(BaseConhecimentoGolpes base, AnaliseIaGolpe ia,
			VerificacaoGolpeRepository verificacoes) {
		this.base = base;
		this.ia = ia;
		this.verificacoes = verificacoes;
	}

	@Transactional
	public VerificacaoResponse verificar(VerificacaoRequest request) {
		CanalMensagem canal = request.canal() == null ? CanalMensagem.OUTRO : request.canal();
		RegrasGolpe.Resultado regras = RegrasGolpe.analisar(request.texto());
		List<DocumentoConhecimento> orientacoes = base.buscar(request.texto(), ORIENTACOES_RECUPERADAS);
		Optional<RespostaIa> analiseIa = ia.analisar(request.texto(), canal, orientacoes);

		VerificacaoResponse resposta = analiseIa
			.map(r -> combinar(r, regras, orientacoes))
			.orElseGet(() -> somenteRegras(regras, orientacoes));

		verificacoes.save(new VerificacaoGolpe(resposta.tipoGolpe(), resposta.classificacao(), canal,
				resposta.analisadoPor(), Instant.now()));
		return resposta;
	}

	@Transactional(readOnly = true)
	public List<EstatisticaGolpeResponse> estatisticas(int dias) {
		Instant desde = Instant.now().minus(Duration.ofDays(dias));
		return verificacoes.contarGolpesPorTipo(desde).stream()
			.map(t -> new EstatisticaGolpeResponse(t.getTipoGolpe(), t.getTotal()))
			.toList();
	}

	private VerificacaoResponse combinar(RespostaIa respostaIa, RegrasGolpe.Resultado regras,
			List<DocumentoConhecimento> orientacoes) {
		ClassificacaoGolpe classificacao = ClassificacaoGolpe.maiorRisco(respostaIa.classificacao(), regras.piso());
		boolean regrasElevaramORisco = classificacao != respostaIa.classificacao();

		TipoGolpe tipo = respostaIa.tipoGolpe() != null ? respostaIa.tipoGolpe() : regras.tipoProvavel();
		if (classificacao.indicaRisco() && tipo == TipoGolpe.NENHUM) {
			tipo = regras.tipoProvavel() == TipoGolpe.NENHUM ? TipoGolpe.OUTRO : regras.tipoProvavel();
		}

		// Se as regras elevaram o risco, a frase da IA (que dizia algo mais brando) não pode ser usada.
		String mensagemFalada = regrasElevaramORisco || !StringUtils.hasText(respostaIa.mensagemFalada())
				? FRASES_PADRAO.get(classificacao) : limitar(respostaIa.mensagemFalada());
		String orientacao = regrasElevaramORisco || !StringUtils.hasText(respostaIa.orientacao())
				? ORIENTACAO_PADRAO : respostaIa.orientacao();
		String canalOficial = canalConfiavel(respostaIa.canalOficial(), orientacoes);

		Set<String> sinais = new LinkedHashSet<>();
		if (respostaIa.sinais() != null) {
			sinais.addAll(respostaIa.sinais());
		}
		sinais.addAll(regras.descricoes());

		return new VerificacaoResponse(classificacao, tipo, sinais.stream().limit(MAXIMO_SINAIS).toList(), orientacao,
				canalOficial, mensagemFalada, fontes(orientacoes), "IA_E_REGRAS", respostaIa.confianca());
	}

	private VerificacaoResponse somenteRegras(RegrasGolpe.Resultado regras, List<DocumentoConhecimento> orientacoes) {
		ClassificacaoGolpe classificacao = regras.piso() != null ? regras.piso() : ClassificacaoGolpe.NAO_E_POSSIVEL_AVALIAR;
		return new VerificacaoResponse(classificacao, regras.tipoProvavel(), regras.descricoes(), ORIENTACAO_PADRAO,
				canalDasOrientacoes(orientacoes), FRASES_PADRAO.get(classificacao), fontes(orientacoes), "REGRAS", null);
	}

	/**
	 * O canal oficial sempre vem da base curada, nunca do texto do modelo: um contato inventado (ou
	 * injetado na mensagem analisada) nunca é recomendado como oficial. A sugestão do modelo só ajuda a
	 * escolher qual das orientações recuperadas usar.
	 */
	static String canalConfiavel(String sugeridoPelaIa, List<DocumentoConhecimento> orientacoes) {
		if (StringUtils.hasText(sugeridoPelaIa)) {
			String sugerido = RegrasGolpe.normalizar(sugeridoPelaIa).strip();
			for (DocumentoConhecimento orientacao : orientacoes) {
				String canal = RegrasGolpe.normalizar(orientacao.canalOficial()).strip();
				if (canal.contains(sugerido) || sugerido.contains(canal)) {
					return orientacao.canalOficial();
				}
			}
		}
		return canalDasOrientacoes(orientacoes);
	}

	private static String canalDasOrientacoes(List<DocumentoConhecimento> orientacoes) {
		return orientacoes.isEmpty() ? null : orientacoes.getFirst().canalOficial();
	}

	private static List<VerificacaoResponse.Fonte> fontes(List<DocumentoConhecimento> orientacoes) {
		return orientacoes.stream()
			.map(d -> new VerificacaoResponse.Fonte(d.titulo(), d.fonte().orgao(), d.fonte().url()))
			.toList();
	}

	static String limitar(String frase) {
		String limpa = frase.strip();
		if (limpa.length() <= MAXIMO_MENSAGEM_FALADA) {
			return limpa;
		}
		int corte = limpa.lastIndexOf(' ', MAXIMO_MENSAGEM_FALADA - 1);
		return limpa.substring(0, corte > 0 ? corte : MAXIMO_MENSAGEM_FALADA - 1) + "…";
	}

}
