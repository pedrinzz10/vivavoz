package br.com.fiap.vozviva.ai;

import br.com.fiap.vozviva.ai.BaseServicosPublicos.ServicoPublico;
import br.com.fiap.vozviva.model.TipoAtendimento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BaseServicosPublicosTest {

	private final BaseServicosPublicos base = new BaseServicosPublicos(JsonMapper.builder().build());

	@Test
	void todoServicoTemOrientacaoOficialCurtaParaLerEmVozAlta() {
		assertThat(base.tamanho()).isGreaterThanOrEqualTo(15);
		assertThat(base.buscar("aposentadoria")).isPresent();
		java.util.List<ServicoPublico> todos = new java.util.ArrayList<>();
		for (String assunto : new String[] { "aposentadoria", "pagamento inss", "prova de vida", "pericia", "bpc idoso",
				"bpc deficiencia", "cadastro unico", "carteira do idoso", "passe livre", "assinar documento",
				"senha gov", "farmacia popular", "conta de luz", "vaga de estacionamento", "maus tratos" }) {
			base.buscar(assunto).ifPresent(todos::add);
		}
		todos.add(base.geral());
		assertThat(todos).allSatisfy(s -> {
			assertThat(s.resumo()).isNotBlank().hasSizeLessThanOrEqualTo(200);
			assertThat(s.link()).startsWith("https://");
			assertThat(s.passos()).isNotEmpty();
			assertThat(s.orgao()).isNotBlank();
		});
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"onde eu acho um lugar para resgatar minha aposentadoria | aposentadoria-inss",
			"como assinar documentos no gov | assinatura-gov",
			"quando cai o pagamento do INSS | pagamento-beneficio-inss",
			"preciso fazer prova de vida | prova-de-vida",
			"esqueci minha senha do gov.br | conta-gov",
			"meu filho autista tem direito a algum benefício | bpc-deficiencia",
			"como pego remédio de graça | farmacia-popular",
			"tenho direito a desconto na conta de luz | tarifa-social-energia",
			"quero denunciar maus tratos | denuncia-violencia",
			"como faco o cadastro unico | cadastro-unico",
			"Como pedir aposentadoria com conta gov.br? | aposentadoria-inss",
			"esqueci a senha da minha conta gov | conta-gov",
			"minha mãe nunca trabalhou de carteira, ela recebe alguma coisa | bpc-idoso"
	})
	void encontraOServicoCerto(String fala, String idEsperado) {
		assertThat(base.buscar(fala)).map(ServicoPublico::id).contains(idEsperado);
	}

	@ParameterizedTest
	@ValueSource(strings = { "caí e não consigo levantar", "tem um buraco na calçada", "quanto custa o pão",
			"hospital com rampa perto", "é... não sei" })
	void falaSemRelacaoComServicoNaoInventaOrientacao(String fala) {
		assertThat(base.buscar(fala)).isEmpty();
	}

	@Test
	void todoServicoFoiRevisadoNosUltimos180Dias() {
		// Regras de benefício e links mudam: este teste falha de propósito quando a base fica velha, para alguém
		// conferir de novo nas fontes oficiais e atualizar o revisadoEm.
		LocalDate limite = LocalDate.now().minusDays(180);
		assertThat(base.todos()).allSatisfy(s -> assertThat(s.revisadoEm()).as(s.id()).isAfter(limite));
		assertThat(base.geral().revisadoEm()).isAfter(limite);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"como eu tiro o cartão do SUS? | cartao-sus",
			"perdi minha identidade, como tiro a segunda via? | carteira-identidade",
			"como tiro a segunda via do meu RG? | carteira-identidade",
			"como renovo a carteira de motorista depois dos 70 anos? | renovar-cnh",
			"como peço um advogado de graça? | advogado-gratuito",
			"idoso tem prioridade na fila do banco? | direitos-pessoa-idosa",
			"pessoa com deficiência paga meia entrada no cinema? | direitos-pessoa-deficiencia"
	})
	void encontraOsServicosNovos(String fala, String idEsperado) {
		assertThat(base.buscar(fala)).map(ServicoPublico::id).contains(idEsperado);
	}

	@Test
	void todoTermoTemPalavraQueOIndiceConsegueLer() {
		// "rg" tem só 2 letras e o índice ignora palavras curtas: um termo assim casaria com qualquer fala.
		assertThat(base.todos()).allSatisfy(s -> assertThat(s.termos())
			.as(s.id())
			.allSatisfy(t -> assertThat(IndiceTfIdf.contar(t)).as(t).isNotEmpty()));
	}

	@Test
	void orientacaoGeralIndicaOCrasEODisqueSocial() {
		assertThat(base.geral().telefone()).isEqualTo("121");
		assertThat(base.geral().localPresencial()).isEqualTo(TipoAtendimento.CRAS);
	}

}
