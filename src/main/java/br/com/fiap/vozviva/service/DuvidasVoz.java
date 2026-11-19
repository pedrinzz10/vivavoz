package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.ai.BaseServicosPublicos;
import br.com.fiap.vozviva.ai.BaseServicosPublicos.ServicoPublico;
import br.com.fiap.vozviva.ai.IndiceTfIdf;
import br.com.fiap.vozviva.ai.RegrasIntencao;
import br.com.fiap.vozviva.ai.RespostaIaDuvida;
import br.com.fiap.vozviva.ai.TurnoAnterior;
import br.com.fiap.vozviva.dto.OrientacaoServicoResponse;
import br.com.fiap.vozviva.model.EntidadesFala;
import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.model.TipoAtendimento;
import br.com.fiap.vozviva.model.Urgencia;
import br.com.fiap.vozviva.service.IntencaoVozService.Decisao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Dúvidas por voz (TIRAR_DUVIDA): liga a decisão à base curada de serviços públicos, usa a conversa recente quando a
 * pergunta continua o assunto e pede à IA a resposta à pergunta feita.
 * <ul>
 *   <li>Passos, link e telefone vêm sempre da base, nunca do modelo; numa dúvida fora da base, vem só o texto.</li>
 *   <li>Socorro, checagem de golpe e problema na rua nunca viram dúvida.</li>
 *   <li>Sem IA (fora do ar ou acima do limite de uso), a orientação da base responde sozinha.</li>
 * </ul>
 */
@Component
public class DuvidasVoz {

	/** Busca sem o tipo de lugar: perguntar, em vez de pedir a localização (que não ajudaria). */
	static final String PERGUNTA_TIPO_ATENDIMENTO = "Que lugar você procura? Posso achar hospital, UPA, posto de saúde, "
			+ "farmácia, CRAS, delegacia ou Defensoria Pública.";
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


	private final BaseServicosPublicos servicos;
	private final RespostaIaDuvida respostas;
	private final double confiancaMinima;

	public DuvidasVoz(BaseServicosPublicos servicos, RespostaIaDuvida respostas,
			@Value("${vozviva.voz.confianca-minima:0.6}") double confiancaMinima) {
		this.servicos = servicos;
		this.respostas = respostas;
		this.confiancaMinima = confiancaMinima;
	}

	/**
	 * A decisão final e a orientação mostrada no app. Qualquer dúvida recebe uma resposta à pergunta feita (com os
	 * fatos da base, quando ela tem o serviço); sem resposta segura da IA, fica a orientação da base.
	 *
	 * @param podeChamarIa consultado só quando a resposta precisa do modelo (cada chamada conta no limite de uso)
	 */
	public Respondida responder(Decisao decidida, String fala, List<TurnoAnterior> anteriores,
			BooleanSupplier podeChamarIa) {
		Orientada orientada = orientar(decidida, fala, anteriores.stream().map(TurnoAnterior::fala).toList(), servicos);
		Decisao decisao = orientada.decisao();
		OrientacaoServicoResponse orientacao = orientada.servico() == null ? null
				: OrientacaoServicoResponse.de(orientada.servico());
		boolean duvida = decidida.intencao() == Intencao.TIRAR_DUVIDA || decisao.intencao() == Intencao.TIRAR_DUVIDA;
		if (!duvida || !podeChamarIa.getAsBoolean()) {
			return new Respondida(decisao, orientacao);
		}
		ServicoPublico daBase = orientada.servico() == null || orientada.servico().id().equals(servicos.geral().id())
				? null : orientada.servico();
		Optional<String> resposta = respostas.responder(fala, anteriores, daBase);
		if (resposta.isEmpty()) {
			return new Respondida(decisao, orientacao);
		}
		EntidadesFala entidades = daBase != null ? decisao.entidades() : EntidadesFala.NENHUMA;
		Urgencia urgencia = decisao.urgencia() == null ? Urgencia.BAIXA : decisao.urgencia();
		return new Respondida(
				new Decisao(Intencao.TIRAR_DUVIDA, urgencia, entidades, resposta.get(),
						Math.max(decisao.confianca(), confiancaMinima)),
				daBase != null ? OrientacaoServicoResponse.de(daBase, resposta.get())
						: OrientacaoServicoResponse.aberta(resposta.get()));
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
		return orientar(decisao, fala, List.of(), servicos);
	}

	/**
	 * @param falasAnteriores falas recentes da mesma pessoa (da mais antiga para a mais nova): numa dúvida que não cita
	 *                        o serviço ("e quanto tempo demora?"), vale o serviço da fala anterior mais recente
	 */
	static Orientada orientar(Decisao decisao, String fala, List<String> falasAnteriores, BaseServicosPublicos servicos) {
		Intencao intencao = decisao.intencao();
		TipoAtendimento tipoPedido = decisao.entidades().tipoAtendimento();
		boolean buscaSemTipo = intencao == Intencao.BUSCAR_ATENDIMENTO && tipoPedido == null;
		boolean mensagemRecebida = MENSAGEM_RECEBIDA.matcher(IndiceTfIdf.normalizar(fala)).find();
		ServicoPublico servico = null;
		if (intencao == Intencao.TIRAR_DUVIDA) {
			boolean sobreServicoPublico = SERVICO_PUBLICO.matcher(IndiceTfIdf.normalizar(fala)).find();
			servico = servicos.buscar(fala).or(() -> doContexto(falasAnteriores, servicos))
				.orElse(sobreServicoPublico ? servicos.geral() : null);
			if (servico == null) {
				return new Orientada(new Decisao(Intencao.DESCONHECIDA, Urgencia.BAIXA, EntidadesFala.NENHUMA,
						IntencaoVozService.PERGUNTA_REPETICAO, decisao.confianca()), null);
			}
		}
		else if ((intencao == Intencao.BUSCAR_ATENDIMENTO || intencao == Intencao.DESCONHECIDA) && !mensagemRecebida) {
			// "Onde fica a Defensoria mais perto?" pede o lugar: se ele já é o atendimento presencial do serviço, segue
			// como busca (com a lista); "remédio de graça na Farmácia Popular" pede a orientação.
			servico = servicos.buscar(fala)
				.filter(s -> tipoPedido == null || s.localPresencial() != tipoPedido)
				.orElse(null);
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
					IntencaoVozService.FRASES_PADRAO.get(Intencao.VERIFICAR_INFORMACAO), RegrasIntencao.CONFIANCA_REGRAS), null);
		}
		if (buscaSemTipo) {
			return new Orientada(new Decisao(intencao, decisao.urgencia(), decisao.entidades(), PERGUNTA_TIPO_ATENDIMENTO,
					decisao.confianca()), null);
		}
		return new Orientada(decisao, null);
	}

	private static Optional<ServicoPublico> doContexto(List<String> falasAnteriores, BaseServicosPublicos servicos) {
		for (int i = falasAnteriores.size() - 1; i >= 0; i--) {
			Optional<ServicoPublico> servico = servicos.buscar(falasAnteriores.get(i));
			if (servico.isPresent()) {
				return servico;
			}
		}
		return Optional.empty();
	}

	/** A decisão final e, numa dúvida, o serviço da base que responde. */
	record Orientada(Decisao decisao, ServicoPublico servico) {
	}

	/** O que a API responde: a decisão e, numa dúvida, a orientação para o app. */
	public record Respondida(Decisao decisao, OrientacaoServicoResponse orientacao) {
	}

}
