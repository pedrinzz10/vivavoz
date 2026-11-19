package br.com.fiap.vozviva.ai;

import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.service.IntencaoVozService;
import br.com.fiap.vozviva.model.ProblemaRua;
import br.com.fiap.vozviva.model.TipoAtendimento;
import br.com.fiap.vozviva.model.Urgencia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasIntencaoTest {

	@ParameterizedTest
	@ValueSource(strings = { "Caí e não consigo me levantar", "SOCORRO", "meu pai desmaiou", "estou com dor no peito",
			"liga pro samu", "to passando muito mal", "me ajuda por favor" })
	void pedidosDeSocorroSaoCriticos(String fala) {
		RegrasIntencao.Resultado resultado = RegrasIntencao.analisar(fala);

		assertThat(resultado.intencao()).isEqualTo(Intencao.SOCORRO);
		assertThat(resultado.urgencia()).isEqualTo(Urgencia.CRITICA);
	}

	@Test
	void prontoSocorroELugarNaoPedidoDeSocorro() {
		RegrasIntencao.Resultado resultado = RegrasIntencao.analisar("onde fica o pronto socorro");

		assertThat(resultado.intencao()).isEqualTo(Intencao.BUSCAR_ATENDIMENTO);
		assertThat(resultado.entidades().tipoAtendimento()).isEqualTo(TipoAtendimento.HOSPITAL);
	}

	@ParameterizedTest
	@ValueSource(strings = { "onde fica o pronto-socorro mais perto?", "onde fica o pronto  socorro", "Pronto_Socorro perto" })
	void prontoSocorroComHifenOuEspacosTambemELugar(String fala) {
		RegrasIntencao.Resultado resultado = RegrasIntencao.analisar(fala);

		assertThat(resultado.intencao()).isEqualTo(Intencao.BUSCAR_ATENDIMENTO);
		assertThat(resultado.entidades().tipoAtendimento()).isEqualTo(TipoAtendimento.HOSPITAL);
	}

	@Test
	void prontoSocorroNaoEscondeUmPedidoDeSocorroDeVerdade() {
		assertThat(RegrasIntencao.analisar("caí, me leva no pronto-socorro").intencao()).isEqualTo(Intencao.SOCORRO);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"me ajuda a encontrar uma farmácia                      | FARMACIA",
			"Preciso de ajuda com o Bolsa Família, onde fica o CRAS? | CRAS" })
	void pedidoGenericoDeAjudaComOutroObjetivoNaoESocorro(String fala, TipoAtendimento tipo) {
		RegrasIntencao.Resultado resultado = RegrasIntencao.analisar(fala);

		assertThat(resultado.intencao()).isEqualTo(Intencao.BUSCAR_ATENDIMENTO);
		assertThat(resultado.entidades().tipoAtendimento()).isEqualTo(tipo);
	}

	@Test
	void arvoreQueCaiuEProblemaNaRuaNaoSocorro() {
		RegrasIntencao.Resultado resultado = RegrasIntencao.analisar("a árvore caiu na calçada");

		assertThat(resultado.intencao()).isEqualTo(Intencao.ZELADORIA_REPORT);
		assertThat(resultado.entidades().problemaRua()).isEqualTo(ProblemaRua.ARVORE_CAIDA);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
			"tem farmácia perto?                         | FARMACIA  | false",
			"hospital com rampa para cadeira de rodas    | HOSPITAL  | true",
			"quero ir no postinho                        | UBS       | false",
			"qual a UPA mais perto                       | UPA       | false",
			"preciso fazer um boletim de ocorrência      | DELEGACIA | false" })
	void buscaDeAtendimentoExtraiOTipoEAAcessibilidade(String fala, TipoAtendimento tipo, boolean acessivel) {
		RegrasIntencao.Resultado resultado = RegrasIntencao.analisar(fala);

		assertThat(resultado.intencao()).isEqualTo(Intencao.BUSCAR_ATENDIMENTO);
		assertThat(resultado.entidades().tipoAtendimento()).isEqualTo(tipo);
		assertThat(resultado.entidades().exigeAcessibilidade()).isEqualTo(acessivel);
	}

	@Test
	void buracoQueImpedeACadeiraEUrgenciaAlta() {
		RegrasIntencao.Resultado resultado = RegrasIntencao.analisar(
				"Tem um buraco gigante na minha calçada e não passo com a cadeira");

		assertThat(resultado.intencao()).isEqualTo(Intencao.ZELADORIA_REPORT);
		assertThat(resultado.urgencia()).isEqualTo(Urgencia.ALTA);
		assertThat(resultado.entidades().problemaRua()).isEqualTo(ProblemaRua.BURACO);
		assertThat(resultado.entidades().impedeCadeirante()).isTrue();
	}

	@Test
	void duasIntencoesAoMesmoTempoPedemRepeticao() {
		assertThat(RegrasIntencao.analisar("recebi uma mensagem do hospital").intencao()).isEqualTo(Intencao.DESCONHECIDA);
	}

	@Test
	void falaSemSentidoClaroEDesconhecida() {
		assertThat(RegrasIntencao.analisar("bom dia, tudo bem?").reconhecida()).isFalse();
	}

	@Test
	void linhaDeBaseDasRegrasNoConjuntoRotulado() {
		// Sem IA, a API responde com as regras e a base de serviços públicos (as dúvidas vêm da base, não das regras).
		BaseServicosPublicos servicos = new BaseServicosPublicos(JsonMapper.builder().build());
		var metricas = FrasesRotuladas.avaliar(FrasesRotuladas.carregar(),
				f -> IntencaoVozService.classificarSemIa(f.texto(), servicos));
		metricas.salvar("somente-regras.md", "Avaliação: sem IA, só regras determinísticas e base de serviços (linha de base)");

		// O erro mais grave é não reconhecer um pedido de socorro: as regras precisam pegar todos do conjunto.
		assertThat(metricas.recallSocorro()).isEqualTo(1.0);
		assertThat(metricas.socorrosFalsos()).isZero();
		assertThat(metricas.acuracia()).isGreaterThanOrEqualTo(0.85);
	}

}
