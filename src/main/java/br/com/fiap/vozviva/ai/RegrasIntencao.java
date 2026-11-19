package br.com.fiap.vozviva.ai;

import br.com.fiap.vozviva.model.EntidadesFala;
import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.model.ProblemaRua;
import br.com.fiap.vozviva.model.TipoAtendimento;
import br.com.fiap.vozviva.model.Urgencia;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras determinísticas para a fala transcrita. Têm dois papéis:
 * <ul>
 *   <li><b>piso de segurança:</b> um pedido de socorro reconhecido aqui sempre vira SOCORRO, mesmo que o
 *   modelo erre ou esteja fora do ar;</li>
 *   <li><b>plano B:</b> sem IA, classificam as falas mais comuns; o que não reconhecem com segurança vira
 *   pedido de repetição.</li>
 * </ul>
 * Trabalham sobre o texto em minúsculas e sem acentos (a transcrição muitas vezes já vem sem acento).
 */
public final class RegrasIntencao {

	/** Confiança atribuída a uma classificação feita só pelas regras. */
	public static final double CONFIANCA_REGRAS = 0.7;

	/** "Quando cai o pagamento": dinheiro que entra na conta, não uma queda (como "pronto socorro" é um lugar). */
	private static final Pattern DINHEIRO_QUE_CAI = Pattern.compile(
			"\\bcai (o |a |na |no |meu |minha )?(pagamento|beneficio|aposentadoria|dinheiro|salario|pensao|conta|deposito|bpc|loas)\\b");

	/** Sinais de perigo agora: sempre viram SOCORRO. */
	private static final Pattern SOCORRO = padrao(
			"\\b(socorro|me acuda|emergencia)\\b",
			"\\b(eu )?cai\\b|\\blevei um tombo\\b",
			"\\b(mae|pai|vo|avo|vovo|marido|esposa|mulher|filho|filha|irmao|irma|ele|ela|alguem|senhor|senhora|vizinho|vizinha) (caiu|desmaiou|passou mal)\\b",
			"\\bnao consigo (levantar|respirar|me mexer|mexer|andar|falar)\\b",
			"\\b(passando|estou|to|ta|esta) (muito )?mal\\b",
			"\\bdor (muito )?(forte )?no peito\\b|\\binfarto\\b|\\bderrame\\b|\\bavc\\b|\\bdesmai|\\bconvuls|\\bsangrando\\b|\\bengasg|\\bfalta de ar\\b",
			"\\b(chama|chame|liga|ligue|chamar|ligar) (a |o |pro |para o |para a )?(ambulancia|samu|192|bombeiros?)\\b");

	/**
	 * Pedido genérico de ajuda: só vira SOCORRO quando a fala não pede outra coisa ("me ajuda a achar uma
	 * farmácia" é uma busca; "me ajuda, por favor" é socorro).
	 */
	private static final Pattern AJUDA_GENERICA = padrao("\\bme (ajuda|ajude)\\b", "\\bpreciso de ajuda\\b",
			"\\bajuda urgente\\b");

	/** Hífens, sublinhados e espaços repetidos viram um espaço: "pronto-socorro" e "pronto  socorro" são iguais. */
	private static final Pattern SEPARADORES = Pattern.compile("[\\s\\-_]+");

	private static final Map<TipoAtendimento, Pattern> ATENDIMENTOS = new LinkedHashMap<>();

	static {
		ATENDIMENTOS.put(TipoAtendimento.UPA, padrao("\\bupa\\b", "\\bpronto atendimento\\b"));
		ATENDIMENTOS.put(TipoAtendimento.HOSPITAL, padrao("\\bhospita", "\\bpronto socorro\\b", "\\bmaternidade\\b"));
		ATENDIMENTOS.put(TipoAtendimento.UBS, padrao("\\bubs\\b", "\\bposto de saude\\b", "\\bpostinho\\b", "\\bunidade basica\\b"));
		ATENDIMENTOS.put(TipoAtendimento.FARMACIA, padrao("\\bfarmacia", "\\bdrogaria", "\\bcomprar (um |o |meu )?remedio"));
		ATENDIMENTOS.put(TipoAtendimento.DELEGACIA, padrao("\\bdelegacia", "\\bboletim de ocorrencia\\b", "\\bfazer (um )?b\\.?o\\b"));
		ATENDIMENTOS.put(TipoAtendimento.CRAS, padrao("\\bcras\\b", "\\bassistencia social\\b", "\\bassistente social\\b"));
		ATENDIMENTOS.put(TipoAtendimento.DEFENSORIA, padrao("\\bdefensoria", "\\badvogado (de graca|gratuito)\\b"));
	}

	private static final Pattern ACESSIBILIDADE = padrao("\\bcadeira de rodas\\b", "\\bcadeirante\\b", "\\bacessiv",
			"\\bacessibilidade\\b", "\\brampa\\b", "\\bandador\\b", "\\bmuleta", "\\bnao enxergo\\b");

	private static final Pattern VERIFICACAO = padrao("\\bgolpe\\b", "\\be verdade\\b", "\\bsera que e (verdade|golpe|serio)\\b",
			"\\brecebi (uma |um )?(mensagem|ligacao|sms|whats|whatsapp|zap|email|e-mail|link|audio|cobranca)\\b",
			"\\bme (mandaram|ligaram|enviaram)\\b", "\\be confiavel\\b", "\\bfake\\b", "\\bnoticia falsa\\b",
			"\\b(troquei|trocou|mudei|mudou) de (numero|celular)\\b");

	private static final Map<ProblemaRua, Pattern> PROBLEMAS = new LinkedHashMap<>();

	static {
		PROBLEMAS.put(ProblemaRua.RAMPA_BLOQUEADA, padrao("\\brampa (quebrada|bloqueada|tampada|com carro|ocupada)\\b",
				"\\bcarro (na|em cima da|parado na) (calcada|rampa)\\b"));
		PROBLEMAS.put(ProblemaRua.BURACO, padrao("\\bburaco", "\\bcratera\\b"));
		PROBLEMAS.put(ProblemaRua.CALCADA_DANIFICADA, padrao(
				"\\bcalcada\\b.{0,20}\\b(quebrada|esburacada|danificada|levantada|irregular|ruim|destruida)\\b",
				"\\bpiso (solto|quebrado)\\b"));
		PROBLEMAS.put(ProblemaRua.ILUMINACAO, padrao("\\bposte\\b", "\\blampada\\b", "\\biluminacao\\b", "\\bluz da rua\\b",
				"\\brua (muito )?escura\\b"));
		PROBLEMAS.put(ProblemaRua.LIXO_OU_ENTULHO, padrao("\\blixo\\b", "\\bentulho\\b"));
		PROBLEMAS.put(ProblemaRua.ARVORE_CAIDA, padrao("\\barvore (caida|caiu|tombou)\\b", "\\bgalho (caido|caiu|grande)\\b"));
		PROBLEMAS.put(ProblemaRua.ALAGAMENTO, padrao("\\balag", "\\benchente\\b", "\\bbueiro\\b"));
	}

	private static final Pattern IMPEDE_PASSAGEM = padrao("\\bnao (passo|consigo passar|da para passar|da pra passar)\\b",
			"\\bcadeira de rodas\\b", "\\bcadeirante\\b", "\\bandador\\b", "\\bbloqueando a passagem\\b");

	private RegrasIntencao() {
	}

	public static Resultado analisar(String fala) {
		String texto = SEPARADORES.matcher(IndiceTfIdf.normalizar(fala)).replaceAll(" ").strip();
		// "Pronto socorro" é um lugar, não um pedido de socorro.
		String semNomesDeLugar = DINHEIRO_QUE_CAI.matcher(texto.replace("pronto socorro", "pronto atendimento"))
			.replaceAll("entra $2");
		if (SOCORRO.matcher(semNomesDeLugar).find()) {
			return socorro();
		}

		Set<Intencao> encontradas = EnumSet.noneOf(Intencao.class);
		TipoAtendimento atendimento = primeiro(ATENDIMENTOS, texto);
		ProblemaRua problema = primeiro(PROBLEMAS, texto);
		if (atendimento != null) {
			encontradas.add(Intencao.BUSCAR_ATENDIMENTO);
		}
		if (problema != null) {
			encontradas.add(Intencao.ZELADORIA_REPORT);
		}
		if (VERIFICACAO.matcher(texto).find()) {
			encontradas.add(Intencao.VERIFICAR_INFORMACAO);
		}
		if (encontradas.isEmpty() && AJUDA_GENERICA.matcher(texto).find()) {
			return socorro();
		}
		if (encontradas.size() != 1) {
			// Nada reconhecido, ou duas coisas ao mesmo tempo: melhor perguntar do que adivinhar.
			return new Resultado(Intencao.DESCONHECIDA, Urgencia.BAIXA, EntidadesFala.NENHUMA);
		}

		return switch (encontradas.iterator().next()) {
			case BUSCAR_ATENDIMENTO -> new Resultado(Intencao.BUSCAR_ATENDIMENTO,
					atendimento == TipoAtendimento.HOSPITAL || atendimento == TipoAtendimento.UPA ? Urgencia.MEDIA : Urgencia.BAIXA,
					new EntidadesFala(atendimento, ACESSIBILIDADE.matcher(texto).find(), null, null, null));
			case ZELADORIA_REPORT -> {
				boolean impede = IMPEDE_PASSAGEM.matcher(texto).find() || problema == ProblemaRua.RAMPA_BLOQUEADA;
				Urgencia urgencia = impede || problema == ProblemaRua.ALAGAMENTO || problema == ProblemaRua.ARVORE_CAIDA
						? Urgencia.ALTA : Urgencia.MEDIA;
				yield new Resultado(Intencao.ZELADORIA_REPORT, urgencia, new EntidadesFala(null, null, problema, impede, null));
			}
			default -> new Resultado(Intencao.VERIFICAR_INFORMACAO, Urgencia.MEDIA, EntidadesFala.NENHUMA);
		};
	}

	private static Resultado socorro() {
		return new Resultado(Intencao.SOCORRO, Urgencia.CRITICA, EntidadesFala.NENHUMA);
	}

	private static <T> T primeiro(Map<T, Pattern> padroes, String texto) {
		return padroes.entrySet().stream()
			.filter(e -> e.getValue().matcher(texto).find())
			.map(Map.Entry::getKey)
			.findFirst()
			.orElse(null);
	}

	private static Pattern padrao(String... alternativas) {
		return Pattern.compile(String.join("|", alternativas), Pattern.DOTALL);
	}

	public record Resultado(Intencao intencao, Urgencia urgencia, EntidadesFala entidades) {

		public boolean reconhecida() {
			return intencao != Intencao.DESCONHECIDA;
		}

	}

}
