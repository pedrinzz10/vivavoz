package br.com.fiap.vozviva.comum.erro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class CodigoErroTest {

	@ParameterizedTest
	@EnumSource(CodigoErro.class)
	void todoCodigoTemMensagemFaladaCurta(CodigoErro codigo) {
		assertThat(codigo.mensagemFalada()).isNotBlank().hasSizeLessThanOrEqualTo(200);
		assertThat(codigo.titulo()).isNotBlank();
	}

	@ParameterizedTest
	@EnumSource(CodigoErro.class)
	void mensagemFaladaSemSiglasTecnicas(CodigoErro codigo) {
		assertThat(codigo.mensagemFalada()).doesNotContainPattern("\\b(HTTP|API|JSON|JWT|ID|URL)\\b");
	}

	@Test
	void paraLogRemoveQuebrasDeLinha() {
		assertThat(GlobalExceptionHandler.paraLog("/api\r\n2026-01-01 ERROR falso")).isEqualTo("/api_2026-01-01 ERROR falso");
		assertThat(GlobalExceptionHandler.paraLog("a\nb\rc d")).isEqualTo("a_b_c_d");
		assertThat(GlobalExceptionHandler.paraLog("Obstrução não encontrada")).isEqualTo("Obstrução não encontrada");
		assertThat(GlobalExceptionHandler.paraLog(null)).isNull();
	}

	@ParameterizedTest
	@CsvSource({
			"400, REQUISICAO_INVALIDA",
			"401, NAO_AUTENTICADO",
			"403, ACESSO_NEGADO",
			"404, RECURSO_NAO_ENCONTRADO",
			"405, METODO_NAO_PERMITIDO",
			"409, CONFLITO",
			"415, TIPO_DE_CONTEUDO_NAO_SUPORTADO",
			"429, MUITAS_TENTATIVAS",
			"503, SERVICO_INDISPONIVEL",
			"500, ERRO_INTERNO",
			"502, ERRO_INTERNO",
			"418, REQUISICAO_INVALIDA"
	})
	void porStatusMapeiaCodigo(int status, CodigoErro esperado) {
		assertThat(CodigoErro.porStatus(status)).isEqualTo(esperado);
	}

}
