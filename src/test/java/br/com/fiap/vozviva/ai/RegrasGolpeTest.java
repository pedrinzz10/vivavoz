package br.com.fiap.vozviva.ai;

import br.com.fiap.vozviva.model.ClassificacaoGolpe;
import br.com.fiap.vozviva.model.TipoGolpe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasGolpeTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"Informe sua senha para desbloquear o benefício",
			"Me passa o código que chegou por SMS",
			"Confirme o token do aplicativo",
			"Faça um Pix urgente de 500 reais",
			"Troquei de número, me manda um pix",
			"Sua conta será bloqueada, clique no link http://site.com"
	})
	void sinaisFortesGarantemProvavelGolpe(String texto) {
		assertThat(RegrasGolpe.analisar(texto).piso()).isEqualTo(ClassificacaoGolpe.PROVAVEL_GOLPE);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Mãe, chego às 19h pra jantar",
			"Lembrete: sua consulta é amanhã às 9h",
			"A vacina contra a gripe é gratuita na UBS"
	})
	void mensagensComunsNaoDisparamRegras(String texto) {
		assertThat(RegrasGolpe.analisar(texto).piso()).isNull();
	}

	@Test
	void ignoraAcentosEMaiusculas() {
		assertThat(RegrasGolpe.analisar("BENEFÍCIO do INSS será BLOQUEADO").sinais())
			.contains(RegrasGolpe.Sinal.ORGAO_OU_BENEFICIO, RegrasGolpe.Sinal.URGENCIA);
	}

	@Test
	void quebraDeLinhaNoMeioDoPedidoNaoEscapaDaRegra() {
		assertThat(RegrasGolpe.analisar("Me envie o código\nque recebeu por SMS").piso())
			.isEqualTo(ClassificacaoGolpe.PROVAVEL_GOLPE);
	}

	@Test
	void linkEncurtadoSozinhoJaESuspeito() {
		RegrasGolpe.Resultado resultado = RegrasGolpe.analisar("Confira as fotos: bit.ly/album");

		assertThat(resultado.piso()).isEqualTo(ClassificacaoGolpe.SUSPEITA);
		assertThat(resultado.tipoProvavel()).isEqualTo(TipoGolpe.LINK_MALICIOSO);
	}

	@Test
	void numeroNovoIndicaFalsoParente() {
		RegrasGolpe.Resultado resultado = RegrasGolpe.analisar("Oi vó, troquei de número, salva esse");

		assertThat(resultado.tipoProvavel()).isEqualTo(TipoGolpe.FALSO_PARENTE);
		assertThat(resultado.piso()).isEqualTo(ClassificacaoGolpe.SUSPEITA);
	}

	@Test
	void linhaDeBaseDasRegrasNoConjuntoRotulado() {
		var metricas = MensagensRotuladas.avaliar(MensagensRotuladas.carregar(), m -> RegrasGolpe.analisar(m.texto()).piso());
		metricas.salvar("somente-regras.md", "Avaliação: somente regras determinísticas (linha de base)");

		// As regras são o piso de segurança: nunca podem gerar falso alarme forte em mensagem legítima.
		assertThat(metricas.falsosPositivos()).isZero();
		assertThat(metricas.recall()).isGreaterThanOrEqualTo(0.6);
	}

}
