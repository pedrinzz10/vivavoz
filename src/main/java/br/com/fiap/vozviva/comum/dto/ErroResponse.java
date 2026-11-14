package br.com.fiap.vozviva.comum.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Contrato de toda resposta de erro da API, no formato Problem Details (RFC 9457)
 * com os campos extras do VozViva.
 *
 * @param type           identificador do tipo de erro (ex.: {@code urn:vozviva:erro:validacao})
 * @param title          título técnico curto
 * @param status         status HTTP
 * @param detail         explicação técnica, sem dados internos
 * @param instance       caminho da requisição que falhou
 * @param codigo         código estável do erro, para o app decidir o que fazer
 * @param mensagemFalada frase simples para ser lida em voz alta ao cidadão
 * @param timestamp      momento do erro (ISO-8601, UTC)
 * @param traceId        identificador para correlacionar com o log do servidor
 * @param campos         campos inválidos (somente em erros de validação)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(
		String type,
		String title,
		int status,
		String detail,
		String instance,
		String codigo,
		String mensagemFalada,
		String timestamp,
		String traceId,
		List<CampoInvalido> campos) {

	public record CampoInvalido(String campo, String mensagem) {
	}

}
