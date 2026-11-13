package br.com.fiap.vozviva.comum.erro;

import org.springframework.http.HttpStatus;

/**
 * Catálogo de erros da API. Cada código tem uma mensagem técnica (título) e uma
 * mensagem simples, curta e sem siglas, pensada para ser lida em voz alta ao cidadão.
 */
public enum CodigoErro {

	VALIDACAO(HttpStatus.BAD_REQUEST, "Dados inválidos",
			"Algumas informações estão faltando ou incorretas. Pode conferir e tentar de novo?"),
	REQUISICAO_INVALIDA(HttpStatus.BAD_REQUEST, "Requisição inválida",
			"Não consegui entender o pedido. Pode tentar de novo?"),
	NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Não autenticado",
			"Você precisa entrar na sua conta de novo para continuar."),
	ACESSO_NEGADO(HttpStatus.FORBIDDEN, "Acesso negado",
			"Você não tem permissão para fazer isso. Peça ajuda ao seu cuidador."),
	RECURSO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Recurso não encontrado",
			"Não encontrei o que você procurou."),
	METODO_NAO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido",
			"Não consegui entender o pedido. Pode tentar de novo?"),
	CONFLITO(HttpStatus.CONFLICT, "Conflito",
			"Isso já foi registrado antes. Não precisa fazer de novo."),
	TIPO_DE_CONTEUDO_NAO_SUPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado",
			"Não consegui entender o pedido. Pode tentar de novo?"),
	SERVICO_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "Serviço indisponível",
			"O serviço está lento agora. Tente de novo em alguns minutos."),
	ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
			"Tivemos um problema. Tente de novo em instantes.");

	private final HttpStatus status;
	private final String titulo;
	private final String mensagemFalada;

	CodigoErro(HttpStatus status, String titulo, String mensagemFalada) {
		this.status = status;
		this.titulo = titulo;
		this.mensagemFalada = mensagemFalada;
	}

	public HttpStatus status() {
		return status;
	}

	public String titulo() {
		return titulo;
	}

	public String mensagemFalada() {
		return mensagemFalada;
	}

	/**
	 * Código usado quando o Spring MVC já decidiu o status (ex.: 404, 405, 415) e não há
	 * um código de domínio associado.
	 */
	public static CodigoErro porStatus(int status) {
		for (CodigoErro codigo : values()) {
			if (codigo.status.value() == status && codigo != VALIDACAO) {
				return codigo;
			}
		}
		return status >= 500 ? ERRO_INTERNO : REQUISICAO_INVALIDA;
	}

}
