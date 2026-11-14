package br.com.fiap.vozviva.voz;

import br.com.fiap.vozviva.atendimento.TipoAtendimento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
		var metricas = FrasesRotuladas.avaliar(FrasesRotuladas.carregar(), f -> RegrasIntencao.analisar(f.texto()).intencao());
		metricas.salvar("somente-regras.md", "Avaliação: somente regras determinísticas (linha de base)");

		// O erro mais grave é não reconhecer um pedido de socorro: as regras precisam pegar todos do conjunto.
		assertThat(metricas.recallSocorro()).isEqualTo(1.0);
		assertThat(metricas.socorrosFalsos()).isZero();
		assertThat(metricas.acuracia()).isGreaterThanOrEqualTo(0.85);
	}

}
