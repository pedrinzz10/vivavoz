package br.com.fiap.vozviva.voz;

import br.com.fiap.vozviva.atendimento.TipoAtendimento;
import br.com.fiap.vozviva.voz.AnaliseIaIntencao.RespostaIa;
import br.com.fiap.vozviva.voz.IntencaoVozService.Decisao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Como a resposta da IA e as regras se combinam (sem Spring e sem modelo real). */
class IntencaoVozServiceTest {

	private static final double LIMITE = 0.6;

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
