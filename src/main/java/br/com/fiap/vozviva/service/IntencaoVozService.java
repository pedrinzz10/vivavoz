package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.ai.AnaliseIaIntencao.RespostaIa;
import br.com.fiap.vozviva.ai.AnaliseIaIntencao;
import br.com.fiap.vozviva.ai.ExemplosIntencao;
import br.com.fiap.vozviva.ai.RegrasIntencao;
import br.com.fiap.vozviva.dto.AtendimentosProximosResponse;
import br.com.fiap.vozviva.dto.IntencaoRequest;
import br.com.fiap.vozviva.dto.IntencaoResponse;
import br.com.fiap.vozviva.dto.InteracaoVozResponse;
import br.com.fiap.vozviva.dto.PaginaResponse;
import br.com.fiap.vozviva.model.EntidadesFala;
import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.model.InteracaoVoz;
import br.com.fiap.vozviva.model.TipoAtendimento;
import br.com.fiap.vozviva.model.Urgencia;
import br.com.fiap.vozviva.repository.InteracaoVozRepository;
import br.com.fiap.vozviva.util.Ordenacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Classifica a fala transcrita e devolve o comando para o app. Combina a IA (Spring AI + exemplos
 * recuperados) com as regras determinísticas:
 * <ul>
 *   <li>socorro reconhecido pelas regras ou pela IA sempre vira SOCORRO (na dúvida, oferecer ajuda);</li>
 *   <li>a IA decide quando está confiante; abaixo do limite, valem as regras;</li>
 *   <li>sem IA e sem regra segura, o app pede para a pessoa repetir.</li>
 * </ul>
 * A API só classifica e orienta: nenhuma ação é executada aqui.
 */
@Service
public class IntencaoVozService {

	static final int MAXIMO_MENSAGEM_FALADA = 200;
	static final String PERGUNTA_REPETICAO = "Não entendi bem. Você quer pedir ajuda, achar um atendimento perto, "
			+ "conferir uma mensagem ou avisar de um problema na rua?";

	private static final int EXEMPLOS_RECUPERADOS = 4;
	private static final int RAIO_ATENDIMENTOS_METROS = 5000;
	private static final int ATENDIMENTOS_NA_RESPOSTA = 3;
	private static final Set<String> ORDENACAO = Set.of("id", "criadaEm");

	private static final Map<Intencao, String> FRASES_PADRAO = Map.of(
			Intencao.SOCORRO, "Entendi que você precisa de ajuda. Confirme na tela para avisar seus contatos, ou ligue 192.",
			Intencao.BUSCAR_ATENDIMENTO, "Vou mostrar os atendimentos mais perto de você.",
			Intencao.VERIFICAR_INFORMACAO, "Me mostre a mensagem que você recebeu, que eu confiro se é golpe.",
			Intencao.ZELADORIA_REPORT, "Vou abrir o aviso de problema na rua para você confirmar.",
			Intencao.DESCONHECIDA, PERGUNTA_REPETICAO);

	private final AnaliseIaIntencao ia;
	private final ExemplosIntencao exemplos;
	private final AtendimentoService atendimentos;
	private final InteracaoVozRepository interacoes;
	private final double confiancaMinima;

	public IntencaoVozService(AnaliseIaIntencao ia, ExemplosIntencao exemplos, AtendimentoService atendimentos,
			InteracaoVozRepository interacoes, @Value("${vozviva.voz.confianca-minima:0.6}") double confiancaMinima) {
		this.ia = ia;
		this.exemplos = exemplos;
		this.atendimentos = atendimentos;
		this.interacoes = interacoes;
		this.confiancaMinima = confiancaMinima;
	}

	public IntencaoResponse classificar(Long usuarioId, IntencaoRequest request) {
		long inicio = System.nanoTime();
		String fala = request.texto().strip();

		RegrasIntencao.Resultado regras = RegrasIntencao.analisar(fala);
		Optional<RespostaIa> respostaIa = ia.analisar(fala, exemplos.parecidos(fala, EXEMPLOS_RECUPERADOS));
		Decisao decisao = decidir(regras, respostaIa.orElse(null), confiancaMinima);

		AtendimentosProximosResponse proximos = buscarAtendimentos(decisao, request);
		// Com a busca feita, vale a frase dela (inclusive "não encontrei..."), nunca uma promessa genérica.
		String mensagemFalada = proximos != null ? proximos.mensagemFalada() : decisao.mensagemFalada();
		String analisadoPor = respostaIa.isPresent() ? "IA_E_REGRAS" : "REGRAS";

		int latenciaMs = (int) ((System.nanoTime() - inicio) / 1_000_000);
		interacoes.save(new InteracaoVoz(usuarioId, fala, decisao.intencao(), decisao.urgencia(), decisao.confianca(),
				latenciaMs, analisadoPor, Instant.now()));

		return new IntencaoResponse(decisao.intencao(), decisao.intencao().acao(), decisao.urgencia(),
				decisao.entidades(), mensagemFalada, decisao.confianca(), analisadoPor, proximos);
	}

	public PaginaResponse<InteracaoVozResponse> historico(Long usuarioId, Pageable pageable) {
		Ordenacao.validar(pageable, ORDENACAO);
		return PaginaResponse.de(interacoes.findByUsuarioId(usuarioId, pageable), i -> new InteracaoVozResponse(
				i.getId(), i.getTexto(), i.getIntencao(), i.getAcao(), i.getUrgencia(),
				i.getConfianca() == null ? null : i.getConfianca().doubleValue(), i.getLatenciaMs(), i.getAnalisadoPor(),
				i.getCriadaEm()));
	}

	static Decisao decidir(RegrasIntencao.Resultado regras, RespostaIa ia, double confiancaMinima) {
		double confiancaIa = ia == null || ia.confianca() == null ? 0 : Math.clamp(ia.confianca(), 0.0, 1.0);
		boolean iaDisseSocorro = ia != null && ia.intencao() == Intencao.SOCORRO;

		// Piso de segurança: na dúvida sobre socorro, o app oferece ajuda (a pessoa confirma na tela).
		if (regras.intencao() == Intencao.SOCORRO || iaDisseSocorro) {
			Urgencia urgencia = regras.intencao() == Intencao.SOCORRO ? Urgencia.CRITICA
					: Urgencia.maior(ia.urgencia(), Urgencia.ALTA);
			String frase = iaDisseSocorro ? ia.mensagemFalada() : null;
			return new Decisao(Intencao.SOCORRO, urgencia, entidadesDa(ia).completarCom(regras.entidades()),
					fraseOuPadrao(frase, Intencao.SOCORRO), Math.max(confiancaIa, RegrasIntencao.CONFIANCA_REGRAS));
		}
		if (ia != null && ia.intencao() != Intencao.DESCONHECIDA && confiancaIa >= confiancaMinima) {
			EntidadesFala entidades = entidadesDa(ia);
			if (regras.intencao() == ia.intencao()) {
				entidades = entidades.completarCom(regras.entidades());
			}
			Urgencia urgencia = ia.urgencia() != null ? ia.urgencia() : regras.urgencia();
			return new Decisao(ia.intencao(), urgencia, entidades, fraseOuPadrao(ia.mensagemFalada(), ia.intencao()), confiancaIa);
		}
		if (regras.reconhecida()) {
			return new Decisao(regras.intencao(), regras.urgencia(), regras.entidades(),
					FRASES_PADRAO.get(regras.intencao()), RegrasIntencao.CONFIANCA_REGRAS);
		}
		return new Decisao(Intencao.DESCONHECIDA, Urgencia.BAIXA, EntidadesFala.NENHUMA, PERGUNTA_REPETICAO, confiancaIa);
	}

	/** Em "farmácia perto" com localização, a resposta já traz os locais: um toque a menos para o cidadão. */
	private AtendimentosProximosResponse buscarAtendimentos(Decisao decisao, IntencaoRequest request) {
		TipoAtendimento tipo = decisao.entidades().tipoAtendimento();
		if (decisao.intencao() != Intencao.BUSCAR_ATENDIMENTO || tipo == null || request.localizacao() == null) {
			return null;
		}
		return atendimentos.proximos(tipo, request.localizacao().latitude(), request.localizacao().longitude(),
				Boolean.TRUE.equals(decisao.entidades().exigeAcessibilidade()), RAIO_ATENDIMENTOS_METROS,
				ATENDIMENTOS_NA_RESPOSTA);
	}

	private static EntidadesFala entidadesDa(RespostaIa ia) {
		return ia == null || ia.entidades() == null ? EntidadesFala.NENHUMA : ia.entidades();
	}

	private static String fraseOuPadrao(String frase, Intencao intencao) {
		if (!StringUtils.hasText(frase)) {
			return FRASES_PADRAO.get(intencao);
		}
		String limpa = frase.strip();
		return limpa.length() <= MAXIMO_MENSAGEM_FALADA ? limpa : FRASES_PADRAO.get(intencao);
	}

	record Decisao(Intencao intencao, Urgencia urgencia, EntidadesFala entidades, String mensagemFalada,
			double confianca) {
	}

}
