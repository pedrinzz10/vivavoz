package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.ai.AnaliseIaGolpe;
import br.com.fiap.vozviva.ai.AnaliseIaGolpe.RespostaIa;
import br.com.fiap.vozviva.ai.BaseConhecimentoGolpes;
import br.com.fiap.vozviva.dto.VerificacaoRequest;
import br.com.fiap.vozviva.dto.VerificacaoResponse;
import br.com.fiap.vozviva.model.CanalMensagem;
import br.com.fiap.vozviva.model.ClassificacaoGolpe;
import br.com.fiap.vozviva.model.TipoGolpe;
import br.com.fiap.vozviva.model.VerificacaoGolpe;
import br.com.fiap.vozviva.repository.VerificacaoGolpeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistenteGolpeServiceTest {

	private final AnaliseIaGolpe ia = mock(AnaliseIaGolpe.class);
	private final VerificacaoGolpeRepository repositorio = mock(VerificacaoGolpeRepository.class);
	private final AssistenteGolpeService servico = new AssistenteGolpeService(
			new BaseConhecimentoGolpes(JsonMapper.builder().build()), ia, repositorio);

	@Test
	void regrasImpedemQueUmPedidoDeSenhaSaiaComoSeguro() {
		iaResponde(new RespostaIa(ClassificacaoGolpe.SEM_SINAIS_DE_RISCO, TipoGolpe.NENHUM, List.of(),
				"Pode responder tranquilo.", null, "Está tudo certo, pode mandar.", 0.9));

		VerificacaoResponse resposta = verificar("Para confirmar o cadastro, me envie sua senha do banco");

		assertThat(resposta.classificacao()).isEqualTo(ClassificacaoGolpe.PROVAVEL_GOLPE);
		assertThat(resposta.mensagemFalada()).doesNotContain("pode mandar").contains("parece golpe");
		assertThat(resposta.orientacao()).doesNotContain("tranquilo");
		assertThat(resposta.tipoGolpe()).isNotEqualTo(TipoGolpe.NENHUM);
	}

	@Test
	void usaAAnaliseDaIaQuandoElaEDeMaiorRisco() {
		iaResponde(new RespostaIa(ClassificacaoGolpe.PROVAVEL_GOLPE, TipoGolpe.FALSA_CENTRAL_BANCO,
				List.of("Pede para entregar o cartão a um motoboy"), "Não entregue o cartão.", "Número no verso do cartão",
				"Isso parece golpe. Banco não busca cartão em casa.", 0.92));

		VerificacaoResponse resposta = verificar("Corte o cartão e entregue ao nosso motoboy");

		assertThat(resposta.classificacao()).isEqualTo(ClassificacaoGolpe.PROVAVEL_GOLPE);
		assertThat(resposta.tipoGolpe()).isEqualTo(TipoGolpe.FALSA_CENTRAL_BANCO);
		assertThat(resposta.mensagemFalada()).isEqualTo("Isso parece golpe. Banco não busca cartão em casa.");
		assertThat(resposta.analisadoPor()).isEqualTo("IA_E_REGRAS");
		assertThat(resposta.confianca()).isEqualTo(0.92);
	}

	@Test
	void canalInventadoPelaIaNuncaViraCanalOficial() {
		iaResponde(new RespostaIa(ClassificacaoGolpe.PROVAVEL_GOLPE, TipoGolpe.FALSO_BENEFICIO, List.of(),
				"Não clique.", "Ligue para (11) 99999-0000, central exclusiva do INSS", "Isso parece golpe.", 0.9));

		VerificacaoResponse resposta = verificar("INSS: seu benefício será bloqueado, atualize seus dados");

		assertThat(resposta.canalOficial()).isNotBlank().doesNotContain("99999");
	}

	@Test
	void semIaRespondeComAsRegrasEAOrientacaoPadrao() {
		when(ia.analisar(any(), any(), any())).thenReturn(Optional.empty());

		VerificacaoResponse resposta = verificar("Faça um Pix urgente agora para liberar seu prêmio");

		assertThat(resposta.analisadoPor()).isEqualTo("REGRAS");
		assertThat(resposta.classificacao()).isEqualTo(ClassificacaoGolpe.PROVAVEL_GOLPE);
		assertThat(resposta.orientacao()).contains("Não clique");
		assertThat(resposta.confianca()).isNull();
	}

	@Test
	void semIaESemSinaisNaoAfirmaQueESeguro() {
		when(ia.analisar(any(), any(), any())).thenReturn(Optional.empty());

		VerificacaoResponse resposta = verificar("Bom dia, tudo bem?");

		assertThat(resposta.classificacao()).isEqualTo(ClassificacaoGolpe.NAO_E_POSSIVEL_AVALIAR);
	}

	@Test
	void fraseFaladaNuncaPassaDe200Caracteres() {
		iaResponde(new RespostaIa(ClassificacaoGolpe.SUSPEITA, TipoGolpe.OUTRO, List.of(), "Confirme.", null,
				"palavra ".repeat(60), 0.6));

		assertThat(verificar("mensagem estranha").mensagemFalada()).hasSizeLessThanOrEqualTo(201);
	}

	@Test
	void gravaSoAEstatisticaSemOTextoDaMensagem() {
		when(ia.analisar(any(), any(), any())).thenReturn(Optional.empty());

		verificar("Me envie a senha, meu CPF é 123.456.789-00");

		ArgumentCaptor<VerificacaoGolpe> gravada = ArgumentCaptor.forClass(VerificacaoGolpe.class);
		verify(repositorio).save(gravada.capture());
		assertThat(gravada.getValue().getClassificacao()).isEqualTo(ClassificacaoGolpe.PROVAVEL_GOLPE);
		assertThat(gravada.getValue()).hasNoNullFieldsOrPropertiesExcept("id")
			.extracting(Object::toString).asString().doesNotContain("123.456.789");
	}

	@Test
	void trazAsFontesDasOrientacoesUsadas() {
		when(ia.analisar(any(), any(), any())).thenReturn(Optional.empty());

		VerificacaoResponse resposta = verificar("Seu benefício do INSS será bloqueado");

		assertThat(resposta.fontes()).isNotEmpty();
		assertThat(resposta.fontes()).extracting(VerificacaoResponse.Fonte::orgao).contains("INSS");
		assertThat(resposta.canalOficial()).isNotBlank();
	}

	private void iaResponde(RespostaIa resposta) {
		when(ia.analisar(any(), any(), any())).thenReturn(Optional.of(resposta));
	}

	private VerificacaoResponse verificar(String texto) {
		return servico.verificar(new VerificacaoRequest(texto, CanalMensagem.WHATSAPP));
	}

}
