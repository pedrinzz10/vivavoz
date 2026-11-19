package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.ai.AnaliseIaIntencao;
import br.com.fiap.vozviva.ai.AnaliseIaIntencao.RespostaIa;
import br.com.fiap.vozviva.ai.BaseServicosPublicos;
import br.com.fiap.vozviva.ai.BaseServicosPublicos.ServicoPublico;
import br.com.fiap.vozviva.ai.ExemplosIntencao;
import br.com.fiap.vozviva.ai.IndiceTfIdf;
import br.com.fiap.vozviva.ai.RegrasIntencao;
import br.com.fiap.vozviva.dto.AtendimentosProximosResponse;
import br.com.fiap.vozviva.dto.IntencaoRequest;
import br.com.fiap.vozviva.dto.IntencaoResponse;
import br.com.fiap.vozviva.dto.InteracaoVozResponse;
import br.com.fiap.vozviva.dto.OrientacaoServicoResponse;
import br.com.fiap.vozviva.dto.PaginaResponse;
import br.com.fiap.vozviva.model.EntidadesFala;
import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.model.InteracaoVoz;
import br.com.fiap.vozviva.model.TipoAtendimento;
import br.com.fiap.vozviva.model.Urgencia;
import br.com.fiap.vozviva.repository.InteracaoVozRepository;
import br.com.fiap.vozviva.util.Ordenacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Classifica a fala transcrita e devolve o comando para o app. Combina a IA (Spring AI + exemplos
 * recuperados) com as regras determinísticas:
 * <ul>
 *   <li>socorro reconhecido pelas regras ou pela IA sempre vira SOCORRO (na dúvida, oferecer ajuda);</li>
 *   <li>a IA decide quando está confiante; abaixo do limite, valem as regras;</li>
 *   <li>dúvida sobre um serviço público (aposentadoria, BPC, gov.br...) recebe a orientação da base curada, com o
 *   link e o telefone oficiais, nunca um texto inventado pelo modelo;</li>
 *   <li>sem IA e sem regra segura, o app pede para a pessoa repetir.</li>
 * </ul>
 * A API só classifica e orienta: nenhuma ação é executada aqui.
 */
@Service
public class IntencaoVozService {

	private static final Logger log = LoggerFactory.getLogger(IntencaoVozService.class);

	static final int MAXIMO_MENSAGEM_FALADA = 200;
	static final String PERGUNTA_REPETICAO = "Não entendi bem. Você quer pedir ajuda, achar um atendimento perto, "
			+ "conferir uma mensagem ou avisar de um problema na rua?";
	/** Busca sem o tipo de lugar: perguntar, em vez de pedir a localização (que não ajudaria). */
	static final String PERGUNTA_TIPO_ATENDIMENTO = "Que lugar você procura? Posso achar hospital, UPA, posto de saúde, "
			+ "farmácia, CRAS, delegacia ou Defensoria Pública.";

	private static final int EXEMPLOS_RECUPERADOS = 4;
	private static final int RAIO_ATENDIMENTOS_METROS = 5000;
	private static final int ATENDIMENTOS_NA_RESPOSTA = 3;
	private static final Set<String> ORDENACAO = Set.of("id", "criadaEm");
	/**
	 * Sinal de que a dúvida é sobre um serviço público, mesmo fora da base ("como tiro o título de eleitor"): só então a
	 * orientação geral (CRAS e Disque Social 121) faz sentido. "Quanto custa o pão" não é dúvida de serviço público.
	 */
	private static final Pattern SERVICO_PUBLICO = Pattern.compile("\\b(gov|governo|prefeitura|inss|sus|cras|cpf|rg|"
			+ "titulo|eleitor|documento|identidade|certidao|beneficio|direito|cadastro|carteira|auxilio|bolsa|pensao|"
			+ "aposentad\\w*|imposto|receita|defensoria|cartorio|detran|consulta|exame|vacina|remedio)\\b");
	/** Fala sobre uma mensagem ou ligação recebida: é checagem de golpe, nunca uma dúvida sobre o serviço citado. */
	private static final Pattern MENSAGEM_RECEBIDA = Pattern.compile(
			"\\b(golpe|golpista|mensagem|link|sms|whatsapp|ligaram|ligacao|pix)\\b|\\be verdade\\b");

	private static final Map<Intencao, String> FRASES_PADRAO = Map.of(
			Intencao.SOCORRO, "Entendi que você precisa de ajuda. Confirme na tela para avisar seus contatos, ou ligue 192.",
			Intencao.BUSCAR_ATENDIMENTO, "Vou mostrar os atendimentos mais perto de você.",
			Intencao.VERIFICAR_INFORMACAO, "Me mostre a mensagem que você recebeu, que eu confiro se é golpe.",
			Intencao.ZELADORIA_REPORT, "Vou abrir o aviso de problema na rua para você confirmar.",
			Intencao.TIRAR_DUVIDA, "Vou mostrar como resolver isso pelos canais oficiais.",
			Intencao.DESCONHECIDA, PERGUNTA_REPETICAO);

	private final AnaliseIaIntencao ia;
	private final ExemplosIntencao exemplos;
	private final BaseServicosPublicos servicos;
	private final AtendimentoService atendimentos;
	private final InteracaoVozRepository interacoes;
	private final double confiancaMinima;

	public IntencaoVozService(AnaliseIaIntencao ia, ExemplosIntencao exemplos, BaseServicosPublicos servicos,
			AtendimentoService atendimentos, InteracaoVozRepository interacoes,
			@Value("${vozviva.voz.confianca-minima:0.6}") double confiancaMinima) {
		this.ia = ia;
		this.exemplos = exemplos;
		this.servicos = servicos;
		this.atendimentos = atendimentos;
		this.interacoes = interacoes;
		this.confiancaMinima = confiancaMinima;
	}

	public IntencaoResponse classificar(Long usuarioId, IntencaoRequest request) {
		long inicio = System.nanoTime();
		String fala = request.texto().strip();

		RegrasIntencao.Resultado regras = RegrasIntencao.analisar(fala);
		Optional<RespostaIa> respostaIa = ia.analisar(fala, exemplos.parecidos(fala, EXEMPLOS_RECUPERADOS));
		Orientada orientada = orientar(decidir(regras, respostaIa.orElse(null), confiancaMinima), fala, servicos);
		Decisao decisao = orientada.decisao();

		AtendimentosProximosResponse proximos = buscarAtendimentos(decisao, request);
		// Com a busca feita, vale a frase dela (inclusive "não encontrei..."), nunca uma promessa genérica. Numa dúvida,
		// a frase é a orientação da base; o lugar mais perto aparece na lista.
		boolean falaDaBusca = proximos != null && decisao.intencao() != Intencao.TIRAR_DUVIDA;
		String mensagemFalada = falaDaBusca ? proximos.mensagemFalada() : decisao.mensagemFalada();
		String analisadoPor = respostaIa.isPresent() ? "IA_E_REGRAS" : "REGRAS";

		int latenciaMs = (int) ((System.nanoTime() - inicio) / 1_000_000);
		registrarNoHistorico(new InteracaoVoz(usuarioId, fala, decisao.intencao(), decisao.urgencia(),
				decisao.confianca(), latenciaMs, analisadoPor, Instant.now()));

		return new IntencaoResponse(decisao.intencao(), decisao.intencao().acao(), decisao.urgencia(),
				decisao.entidades(), mensagemFalada, decisao.confianca(), analisadoPor, proximos,
				orientada.servico() == null ? null : OrientacaoServicoResponse.de(orientada.servico()));
	}

	public PaginaResponse<InteracaoVozResponse> historico(Long usuarioId, Pageable pageable) {
		Ordenacao.validar(pageable, ORDENACAO);
		return PaginaResponse.de(interacoes.findByUsuarioId(usuarioId, pageable), i -> new InteracaoVozResponse(
				i.getId(), i.getTexto(), i.getIntencao(), i.getAcao(), i.getUrgencia(),
				i.getConfianca() == null ? null : i.getConfianca().doubleValue(), i.getLatenciaMs(), i.getAnalisadoPor(),
				i.getCriadaEm()));
	}

	/**
	 * O que a API responde sem IA (fora do ar, sem chave ou timeout): as regras e a base de serviços públicos. É a
	 * linha de base da avaliação ({@code somente-regras.md}).
	 */
	public static Intencao classificarSemIa(String fala, BaseServicosPublicos servicos) {
		return orientar(decidir(RegrasIntencao.analisar(fala), null, 1.0), fala, servicos).decisao().intencao();
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

	/**
	 * Liga a decisão à base de serviços públicos. A resposta de uma dúvida vem sempre da base (com o link e o telefone
	 * oficiais); o socorro, a verificação de golpe e o problema na rua nunca são trocados.
	 * <ul>
	 *   <li>TIRAR_DUVIDA: o serviço mais parecido; fora da base, a orientação geral (CRAS e Disque Social 121) se a fala
	 *   for sobre um serviço público, ou o pedido para repetir se não for ("quanto custa o pão");</li>
	 *   <li>busca de atendimento ou fala não entendida que cite um serviço da base ("onde resolvo minha aposentadoria",
	 *   "remédio de graça na Farmácia Popular") vira TIRAR_DUVIDA, mantendo o tipo de lugar para a busca;</li>
	 *   <li>busca sem tipo e fora da base pergunta que lugar a pessoa procura;</li>
	 *   <li>fala sobre mensagem, link ou ligação ("recebi uma mensagem da Farmácia Popular, é golpe?") nunca vira
	 *   dúvida: a orientação do serviço não pode tomar o lugar da checagem de golpe; se as regras não a entenderam,
	 *   ela vai para a checagem de golpe.</li>
	 * </ul>
	 */
	static Orientada orientar(Decisao decisao, String fala, BaseServicosPublicos servicos) {
		Intencao intencao = decisao.intencao();
		TipoAtendimento tipoPedido = decisao.entidades().tipoAtendimento();
		boolean buscaSemTipo = intencao == Intencao.BUSCAR_ATENDIMENTO && tipoPedido == null;
		boolean mensagemRecebida = MENSAGEM_RECEBIDA.matcher(IndiceTfIdf.normalizar(fala)).find();
		ServicoPublico servico = null;
		if (intencao == Intencao.TIRAR_DUVIDA) {
			boolean sobreServicoPublico = SERVICO_PUBLICO.matcher(IndiceTfIdf.normalizar(fala)).find();
			servico = servicos.buscar(fala).orElse(sobreServicoPublico ? servicos.geral() : null);
			if (servico == null) {
				return new Orientada(new Decisao(Intencao.DESCONHECIDA, Urgencia.BAIXA, EntidadesFala.NENHUMA,
						PERGUNTA_REPETICAO, decisao.confianca()), null);
			}
		}
		else if ((intencao == Intencao.BUSCAR_ATENDIMENTO || intencao == Intencao.DESCONHECIDA) && !mensagemRecebida) {
			servico = servicos.buscar(fala).orElse(null);
		}
		if (servico != null) {
			TipoAtendimento local = servico.localPresencial() != null ? servico.localPresencial() : tipoPedido;
			EntidadesFala entidades = new EntidadesFala(local, decisao.entidades().exigeAcessibilidade(), null, null,
					decisao.entidades().referenciaLocal());
			Urgencia urgencia = decisao.urgencia() == null ? Urgencia.BAIXA : decisao.urgencia();
			return new Orientada(new Decisao(Intencao.TIRAR_DUVIDA, urgencia, entidades, servico.resumo(),
					Math.max(decisao.confianca(), RegrasIntencao.CONFIANCA_REGRAS)), servico);
		}
		if (intencao == Intencao.DESCONHECIDA && mensagemRecebida) {
			// Sem IA, as regras não entenderam, mas a fala é sobre uma mensagem, um link ou um pix: oferecer a checagem
			// de golpe (a pessoa confirma na tela) é melhor do que pedir para repetir.
			return new Orientada(new Decisao(Intencao.VERIFICAR_INFORMACAO, Urgencia.MEDIA, EntidadesFala.NENHUMA,
					FRASES_PADRAO.get(Intencao.VERIFICAR_INFORMACAO), RegrasIntencao.CONFIANCA_REGRAS), null);
		}
		if (buscaSemTipo) {
			return new Orientada(new Decisao(intencao, decisao.urgencia(), decisao.entidades(), PERGUNTA_TIPO_ATENDIMENTO,
					decisao.confianca()), null);
		}
		return new Orientada(decisao, null);
	}

	/**
	 * O histórico é secundário: se não puder ser gravado (ex.: a conta do token não existe mais), a resposta
	 * sai mesmo assim. Um pedido de socorro nunca se perde por causa do histórico.
	 */
	private void registrarNoHistorico(InteracaoVoz interacao) {
		try {
			interacoes.save(interacao);
		}
		catch (DataIntegrityViolationException ex) {
			log.warn("Interação de voz não gravada no histórico (usuário {}): {}", interacao.getUsuarioId(),
					ex.getClass().getSimpleName());
		}
	}

	/**
	 * Em "farmácia perto" com localização, a resposta já traz os locais: um toque a menos para o cidadão. Numa dúvida
	 * com atendimento presencial (ex.: BPC no CRAS), traz o lugar mais perto onde resolver.
	 */
	private AtendimentosProximosResponse buscarAtendimentos(Decisao decisao, IntencaoRequest request) {
		TipoAtendimento tipo = decisao.entidades().tipoAtendimento();
		boolean buscaLocais = decisao.intencao() == Intencao.BUSCAR_ATENDIMENTO || decisao.intencao() == Intencao.TIRAR_DUVIDA;
		if (!buscaLocais || tipo == null || request.localizacao() == null) {
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

	/** A decisão final e, numa dúvida, o serviço da base que responde. */
	record Orientada(Decisao decisao, ServicoPublico servico) {
	}

}
