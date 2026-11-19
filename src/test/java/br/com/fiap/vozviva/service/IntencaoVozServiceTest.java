package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.ai.AnaliseIaIntencao.RespostaIa;
import br.com.fiap.vozviva.ai.BaseServicosPublicos;
import br.com.fiap.vozviva.ai.RegrasIntencao;
import br.com.fiap.vozviva.model.AcaoApp;
import br.com.fiap.vozviva.model.EntidadesFala;
import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.model.TipoAtendimento;
import br.com.fiap.vozviva.model.Urgencia;
import br.com.fiap.vozviva.service.IntencaoVozService.Decisao;
import br.com.fiap.vozviva.service.DuvidasVoz.Orientada;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Como a resposta da IA e as regras se combinam (sem Spring e sem modelo real). */
class IntencaoVozServiceTest {

	private static final double LIMITE = 0.6;
	private static final BaseServicosPublicos SERVICOS = new BaseServicosPublicos(JsonMapper.builder().build());

	private static Orientada orientar(String fala, RespostaIa ia) {
		return DuvidasVoz.orientar(IntencaoVozService.decidir(RegrasIntencao.analisar(fala), ia, LIMITE), fala,
				SERVICOS);
	}

	@Test
	void buscaSemTipoDeLugarSobreAposentadoriaViraOrientacaoDoInss() {
		// Era o loop do app: a IA dizia BUSCAR_ATENDIMENTO sem tipo, e a localização nunca resolvia.
		String fala = "onde eu acho um lugar para resgatar minha aposentadoria";
		RespostaIa ia = new RespostaIa(Intencao.BUSCAR_ATENDIMENTO, Urgencia.MEDIA, EntidadesFala.NENHUMA,
				"Vou ajudar a encontrar um local para resgatar sua aposentadoria", 0.8);

		Orientada orientada = orientar(fala, ia);

		assertThat(orientada.decisao().intencao()).isEqualTo(Intencao.TIRAR_DUVIDA);
		assertThat(orientada.decisao().intencao().acao()).isEqualTo(AcaoApp.MOSTRAR_ORIENTACAO);
		assertThat(orientada.servico().telefone()).isEqualTo("135");
		assertThat(orientada.decisao().mensagemFalada()).isEqualTo(orientada.servico().resumo());
	}

	@Test
	void buscaSemTipoEForaDaBasePerguntaOLugarEmVezDePedirLocalizacao() {
		RespostaIa ia = new RespostaIa(Intencao.BUSCAR_ATENDIMENTO, Urgencia.BAIXA, EntidadesFala.NENHUMA,
				"Vou procurar um lugar para você", 0.8);

		Orientada orientada = orientar("onde fica um lugar bom por aqui", ia);

		assertThat(orientada.decisao().intencao()).isEqualTo(Intencao.BUSCAR_ATENDIMENTO);
		assertThat(orientada.decisao().mensagemFalada()).isEqualTo(DuvidasVoz.PERGUNTA_TIPO_ATENDIMENTO);
		assertThat(orientada.servico()).isNull();
	}

	@Test
	void duvidaRespondidaPelaBaseENuncaPeloTextoDoModelo() {
		RespostaIa ia = new RespostaIa(Intencao.TIRAR_DUVIDA, Urgencia.BAIXA, EntidadesFala.NENHUMA,
				"Entre no site www.assinaturas-gratis.com", 0.9);

		Orientada orientada = orientar("como assinar documentos no gov", ia);

		assertThat(orientada.servico().id()).isEqualTo("assinatura-gov");
		assertThat(orientada.decisao().mensagemFalada()).doesNotContain("assinaturas-gratis").contains("assinador.iti.br");
	}

	@Test
	void duvidaForaDaBaseRecebeAOrientacaoGeral() {
		RespostaIa ia = new RespostaIa(Intencao.TIRAR_DUVIDA, Urgencia.BAIXA, EntidadesFala.NENHUMA, "Vou ver.", 0.9);

		Orientada orientada = orientar("como tiro o título de eleitor", ia);

		assertThat(orientada.servico().id()).isEqualTo("orientacao-geral");
		assertThat(orientada.decisao().entidades().tipoAtendimento()).isEqualTo(TipoAtendimento.CRAS);
	}

	@Test
	void duvidaQueNaoESobreServicoPublicoPedeParaRepetir() {
		RespostaIa ia = new RespostaIa(Intencao.TIRAR_DUVIDA, Urgencia.BAIXA, EntidadesFala.NENHUMA, "Vou ver.", 0.9);

		Orientada orientada = orientar("quanto custa o pão", ia);

		assertThat(orientada.decisao().intencao()).isEqualTo(Intencao.DESCONHECIDA);
		assertThat(orientada.servico()).isNull();
	}

	@Test
	void verificacaoDeGolpeESocorroNuncaViramDuvida() {
		RespostaIa golpe = new RespostaIa(Intencao.VERIFICAR_INFORMACAO, Urgencia.MEDIA, EntidadesFala.NENHUMA,
				"Vou conferir a mensagem.", 0.9);
		assertThat(orientar("recebi mensagem pedindo prova de vida pelo link", golpe).decisao().intencao())
			.isEqualTo(Intencao.VERIFICAR_INFORMACAO);

		assertThat(orientar("caí e não consigo levantar, preciso da aposentadoria", null).decisao().intencao())
			.isEqualTo(Intencao.SOCORRO);
	}

	@Test
	void mensagemRecebidaSobreUmServicoNuncaViraDuvida() {
		// Sem IA, as regras deixam esta fala como DESCONHECIDA; a base não pode trocá-la pela orientação da farmácia.
		Orientada orientada = orientar("Recebi uma mensagem da Farmácia Popular, é golpe?", null);

		assertThat(orientada.decisao().intencao()).isNotEqualTo(Intencao.TIRAR_DUVIDA);
		assertThat(orientada.servico()).isNull();
	}

	@Test
	void beneficioDaFarmaciaPopularViraOrientacaoSemPerderABuscaDaFarmacia() {
		Orientada orientada = orientar("Como pego remédio de graça na Farmácia Popular?", null);

		assertThat(orientada.decisao().intencao()).isEqualTo(Intencao.TIRAR_DUVIDA);
		assertThat(orientada.servico().id()).isEqualTo("farmacia-popular");
		assertThat(orientada.decisao().entidades().tipoAtendimento()).isEqualTo(TipoAtendimento.FARMACIA);
	}

	@Test
	void naoRecebiAAposentadoriaContinuaSendoDuvida() {
		assertThat(IntencaoVozService.classificarSemIa("não recebi minha aposentadoria esse mês", SERVICOS))
			.isEqualTo(Intencao.TIRAR_DUVIDA);
	}

	@Test
	void diaDoPagamentoNaoESocorroMasQuedaContinuaSendo() {
		assertThat(IntencaoVozService.classificarSemIa("que dia cai o pagamento dos aposentados", SERVICOS))
			.isEqualTo(Intencao.TIRAR_DUVIDA);
		assertThat(IntencaoVozService.classificarSemIa("cai no banheiro e não consigo levantar", SERVICOS))
			.isEqualTo(Intencao.SOCORRO);
	}

	@Test
	void semIaMensagemComLinkOuPixVaiParaAChecagemDeGolpe() {
		assertThat(IntencaoVozService.classificarSemIa("disseram que tenho valor do inss e pediram um pix", SERVICOS))
			.isEqualTo(Intencao.VERIFICAR_INFORMACAO);
	}

	@Test
	void continuacaoDaConversaUsaOServicoDaFalaAnterior() {
		RespostaIa ia = new RespostaIa(Intencao.TIRAR_DUVIDA, Urgencia.BAIXA, EntidadesFala.NENHUMA, "Vou ver.", 0.9);
		String fala = "e quanto tempo demora pra sair?";

		Orientada orientada = DuvidasVoz.orientar(IntencaoVozService.decidir(RegrasIntencao.analisar(fala), ia,
				LIMITE), fala, java.util.List.of("quero pedir minha aposentadoria"), SERVICOS);

		assertThat(orientada.servico().id()).isEqualTo("aposentadoria-inss");
	}

	@Test
	void falaQueCitaOutroServicoNaoHerdaODaConversa() {
		RespostaIa ia = new RespostaIa(Intencao.TIRAR_DUVIDA, Urgencia.BAIXA, EntidadesFala.NENHUMA, "Vou ver.", 0.9);
		String fala = "e precisa ter o cadastro único?";

		Orientada orientada = DuvidasVoz.orientar(IntencaoVozService.decidir(RegrasIntencao.analisar(fala), ia,
				LIMITE), fala, java.util.List.of("como tiro a carteira do idoso?"), SERVICOS);

		assertThat(orientada.servico().id()).isEqualTo("cadastro-unico");
	}

	@Test
	void corteDeLuzPorFaltaDePagamentoNaoEPagamentoDoInss() {
		assertThat(SERVICOS.buscar("o que eu faço se cortarem minha luz por falta de pagamento?"))
			.map(s -> s.id())
			.isNotEqualTo(java.util.Optional.of("pagamento-beneficio-inss"));
	}

	@Test
	void semIaADuvidaVemDaBase() {
		assertThat(IntencaoVozService.classificarSemIa("como faço a prova de vida", SERVICOS))
			.isEqualTo(Intencao.TIRAR_DUVIDA);
	}

	@Test
	void iaConfianteDecideEARegraCompletaAsEntidades() {
		RespostaIa ia = new RespostaIa(Intencao.BUSCAR_ATENDIMENTO, Urgencia.MEDIA,
				new EntidadesFala(null, true, null, null, null), "Vou mostrar o hospital mais perto.", 0.9);

		Decisao decisao = IntencaoVozService.decidir(RegrasIntencao.analisar("hospital com rampa"), ia, LIMITE);

		assertThat(decisao.intencao()).isEqualTo(Intencao.BUSCAR_ATENDIMENTO);
		assertThat(decisao.intencao().acao()).isEqualTo(AcaoApp.MOSTRAR_ATENDIMENTOS);
		assertThat(decisao.entidades().tipoAtendimento()).isEqualTo(TipoAtendimento.HOSPITAL);
		assertThat(decisao.entidades().exigeAcessibilidade()).isTrue();
		assertThat(decisao.mensagemFalada()).isEqualTo("Vou mostrar o hospital mais perto.");
	}

	@Test
	void baixaConfiancaPedeRepeticao() {
		RespostaIa ia = new RespostaIa(Intencao.VERIFICAR_INFORMACAO, Urgencia.BAIXA, null, "Vou conferir.", 0.3);

		Decisao decisao = IntencaoVozService.decidir(RegrasIntencao.analisar("hmm, aquilo lá"), ia, LIMITE);

		assertThat(decisao.intencao()).isEqualTo(Intencao.DESCONHECIDA);
		assertThat(decisao.intencao().acao()).isEqualTo(AcaoApp.PEDIR_REPETICAO);
		assertThat(decisao.mensagemFalada()).isEqualTo(IntencaoVozService.PERGUNTA_REPETICAO);
	}

	@Test
	void baixaConfiancaDaIaComRegraSeguraUsaARegra() {
		RespostaIa ia = new RespostaIa(Intencao.VERIFICAR_INFORMACAO, Urgencia.BAIXA, null, null, 0.2);

		Decisao decisao = IntencaoVozService.decidir(RegrasIntencao.analisar("tem um buraco na rua"), ia, LIMITE);

		assertThat(decisao.intencao()).isEqualTo(Intencao.ZELADORIA_REPORT);
		assertThat(decisao.confianca()).isEqualTo(RegrasIntencao.CONFIANCA_REGRAS);
	}

	@Test
	void respostaMalformadaDoLlmSemRegraViraDesconhecida() {
		Decisao decisao = IntencaoVozService.decidir(RegrasIntencao.analisar("blá blá"), null, LIMITE);

		assertThat(decisao.intencao()).isEqualTo(Intencao.DESCONHECIDA);
		assertThat(decisao.intencao().acao()).isEqualTo(AcaoApp.PEDIR_REPETICAO);
	}

	@Test
	void socorroDasRegrasVenceAIa() {
		RespostaIa ia = new RespostaIa(Intencao.BUSCAR_ATENDIMENTO, Urgencia.BAIXA, null, "Vou mostrar farmácias.", 0.95);

		Decisao decisao = IntencaoVozService.decidir(RegrasIntencao.analisar("caí e não consigo levantar"), ia, LIMITE);

		assertThat(decisao.intencao()).isEqualTo(Intencao.SOCORRO);
		assertThat(decisao.urgencia()).isEqualTo(Urgencia.CRITICA);
		assertThat(decisao.mensagemFalada()).doesNotContain("farmácia").contains("192");
	}

	@Test
	void socorroDaIaValeMesmoComPoucaConfianca() {
		RespostaIa ia = new RespostaIa(Intencao.SOCORRO, Urgencia.MEDIA, null, "Quer que eu peça ajuda?", 0.4);

		Decisao decisao = IntencaoVozService.decidir(RegrasIntencao.analisar("não tô bem não"), ia, LIMITE);

		assertThat(decisao.intencao()).isEqualTo(Intencao.SOCORRO);
		assertThat(decisao.urgencia()).isEqualTo(Urgencia.ALTA);
	}

	@Test
	void fraseLongaDemaisDaIaViraAFrasePadrao() {
		RespostaIa ia = new RespostaIa(Intencao.ZELADORIA_REPORT, Urgencia.MEDIA, null, "a".repeat(201), 0.9);

		Decisao decisao = IntencaoVozService.decidir(RegrasIntencao.analisar("poste apagado"), ia, LIMITE);

		assertThat(decisao.mensagemFalada()).hasSizeLessThanOrEqualTo(IntencaoVozService.MAXIMO_MENSAGEM_FALADA);
	}

	@ParameterizedTest
	@EnumSource(Intencao.class)
	void todaIntencaoTemAcaoEFraseCurta(Intencao intencao) {
		RespostaIa ia = new RespostaIa(intencao, Urgencia.BAIXA, null, null, 0.99);

		Decisao decisao = IntencaoVozService.decidir(RegrasIntencao.analisar("qualquer"), ia, LIMITE);

		assertThat(decisao.intencao().acao()).isNotNull();
		assertThat(decisao.mensagemFalada()).isNotBlank().hasSizeLessThanOrEqualTo(IntencaoVozService.MAXIMO_MENSAGEM_FALADA);
	}

}
