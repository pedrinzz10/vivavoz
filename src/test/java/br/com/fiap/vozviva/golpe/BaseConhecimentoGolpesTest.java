package br.com.fiap.vozviva.golpe;

import br.com.fiap.vozviva.golpe.BaseConhecimentoGolpes.DocumentoConhecimento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaseConhecimentoGolpesTest {

	private final BaseConhecimentoGolpes base = new BaseConhecimentoGolpes(JsonMapper.builder().build());

	@Test
	void carregaTodosOsDocumentosComFonte() {
		assertThat(base.tamanho()).isGreaterThanOrEqualTo(12);
		assertThat(base.buscar("golpe", 50)).allSatisfy(d -> {
			assertThat(d.fonte().orgao()).isNotBlank();
			assertThat(d.fonte().url()).startsWith("https://");
			assertThat(d.canalOficial()).isNotBlank();
		});
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Seu benefício do INSS será bloqueado, atualize o cadastro | inss-bloqueio-beneficio",
			"Oi mãe, troquei de número, me faz um pix | falso-parente-numero-novo",
			"Aqui é da central do banco, houve uma compra no seu cartão | banco-falsa-central",
			"Sua encomenda está retida, pague a taxa de entrega | entrega-taxa-correios",
			"Agende sua vacina do SUS pagando a taxa | saude-vacina-cobrada",
			"Você ganhou um prêmio no sorteio | premio-sorteio"
	})
	void recuperaAOrientacaoCertaEntreAsPrimeiras(String mensagem, String idEsperado) {
		List<DocumentoConhecimento> encontrados = base.buscar(mensagem, 3);

		assertThat(encontrados).extracting(DocumentoConhecimento::id).contains(idEsperado);
	}

	@Test
	void textoSemRelacaoNaoTrazNada() {
		assertThat(base.buscar("xyz qwerty", 3)).isEmpty();
	}

	@Test
	void respeitaOLimite() {
		assertThat(base.buscar("banco cartão senha pix golpe", 2)).hasSizeLessThanOrEqualTo(2);
	}

}
