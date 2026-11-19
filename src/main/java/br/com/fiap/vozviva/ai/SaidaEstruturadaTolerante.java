package br.com.fiap.vozviva.ai;

import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê a resposta estruturada do modelo de IA sem descartá-la por um detalhe de formato.
 * <p>
 * O modelo às vezes escreve um valor de lista de outro jeito ("critica" em vez de "CRITICA", "", "N/A") ou acrescenta
 * um campo. Com o leitor padrão, um único valor assim derruba a análise inteira e a resposta sai só com as regras.
 * Aqui, maiúsculas e minúsculas não importam, um valor fora da lista vira nulo, texto vazio vira nulo e campos a mais
 * são ignorados. As regras de segurança continuam valendo depois, sobre o que foi lido.
 */
final class SaidaEstruturadaTolerante {

	private static final JsonMapper LEITOR = JsonMapper.builder()
		.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
		.enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
		.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.build();

	private SaidaEstruturadaTolerante() {
	}

	static <T> BeanOutputConverter<T> para(Class<T> tipo) {
		return new BeanOutputConverter<>(tipo, LEITOR);
	}

}
