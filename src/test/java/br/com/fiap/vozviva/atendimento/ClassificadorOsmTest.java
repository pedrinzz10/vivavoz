package br.com.fiap.vozviva.atendimento;

import br.com.fiap.vozviva.atendimento.importacao.ClassificadorOsm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificadorOsmTest {

	@ParameterizedTest(name = "{0} / {1} = {2}")
	@CsvSource({
			"UPA Centro,                               amenity=hospital,  UPA",
			"Unidade de Pronto Atendimento Lapa,       amenity=clinic,    UPA",
			"Hospital Municipal Vivaldo Martins,       amenity=hospital,  HOSPITAL",
			"Pronto Socorro do Mutinga,                healthcare=hospital, HOSPITAL",
			"UBS Vila Progresso,                       amenity=clinic,    UBS",
			"Unidade Básica de Saúde Vila Helena,      healthcare=centre, UBS",
			"Drogaria São Paulo,                       amenity=pharmacy,  FARMACIA",
			"5º Distrito Policial,                     amenity=police,    DELEGACIA",
			"Delegacia de Defesa da Mulher,            amenity=police,    DELEGACIA",
			"CRAS Jardim Santo Antônio,                amenity=social_facility, CRAS",
			"Centro de Referência de Assistência Social, office=government, CRAS",
			"Defensoria Pública de Osasco,             office=government, DEFENSORIA" })
	void tagsENomeViramOTipo(String nome, String tag, TipoAtendimento esperado) {
		String[] chaveValor = tag.split("=");

		assertThat(ClassificadorOsm.tipo(Map.of("name", nome, chaveValor[0], chaveValor[1]))).contains(esperado);
	}

	@ParameterizedTest(name = "{0} / {1} fica de fora")
	@CsvSource({
			"Consultório Dr. Fulano,          amenity=doctors",
			"Base Comunitária da PM,          amenity=police",
			"Loja de cosméticos UPA,          shop=beauty" })
	void naoReconhecidosFicamDeFora(String nome, String tag) {
		String[] chaveValor = tag.split("=");

		assertThat(ClassificadorOsm.tipo(Map.of("name", nome, chaveValor[0], chaveValor[1]))).isEmpty();
	}

	@Test
	void semNomeFicaDeFora() {
		assertThat(ClassificadorOsm.tipo(Map.of("amenity", "pharmacy"))).isEmpty();
	}

	@ParameterizedTest(name = "wheelchair={0} = {1}")
	@CsvSource({ "yes, SIM", "designated, SIM", "limited, PARCIAL", "no, NAO", "talvez, DESCONHECIDA" })
	void wheelchairViraAcessibilidade(String valor, Acessibilidade esperada) {
		assertThat(ClassificadorOsm.acessibilidade(Map.of("wheelchair", valor))).isEqualTo(esperada);
	}

	@Test
	void semTagDeAcessibilidadeEDesconhecida() {
		assertThat(ClassificadorOsm.acessibilidade(Map.of())).isEqualTo(Acessibilidade.DESCONHECIDA);
	}

	@Test
	void banheiroHorarioEnderecoETelefone() {
		Map<String, String> tags = Map.of("toilets:wheelchair", "yes", "opening_hours", "24/7",
				"addr:street", "Rua Ari Barroso", "addr:housenumber", "355", "addr:city", "Osasco",
				"phone", "+55 11 3651-1900;+55 11 3651-1901");

		assertThat(ClassificadorOsm.banheiroAdaptado(tags)).isTrue();
		assertThat(ClassificadorOsm.funciona24h(tags)).isTrue();
		assertThat(ClassificadorOsm.endereco(tags)).isEqualTo("Rua Ari Barroso, 355 - Osasco");
		assertThat(ClassificadorOsm.telefone(tags)).isEqualTo("+55 11 3651-1900");
	}

	@Test
	void horarioDiferenteNaoE24hESemHorarioFicaSemInformacao() {
		assertThat(ClassificadorOsm.funciona24h(Map.of("opening_hours", "Mo-Fr 08:00-17:00"))).isFalse();
		assertThat(ClassificadorOsm.funciona24h(Map.of())).isNull();
		assertThat(ClassificadorOsm.banheiroAdaptado(Map.of())).isNull();
		assertThat(ClassificadorOsm.endereco(Map.of())).isNull();
		assertThat(ClassificadorOsm.telefone(Map.of("contact:phone", "192"))).isEqualTo("192");
		assertThat(ClassificadorOsm.telefone(Map.of("phone", " ", "contact:phone", "+55 11 1234-5678")))
			.isEqualTo("+55 11 1234-5678");
	}

}
