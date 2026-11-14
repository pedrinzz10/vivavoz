package br.com.fiap.vozviva.comum.erro;

import br.com.fiap.vozviva.comum.dto.ErroResponse;
import br.com.fiap.vozviva.comum.dto.ErroResponse.CampoInvalido;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Tratador central de erros. Toda resposta de erro sai como {@link ErroResponse}
 * (Problem Details + {@code mensagemFalada} e {@code traceId}) e nunca expõe stack traces
 * ou nomes de classes.
 * <p>
 * Exceções do Spring MVC (400, 404, 405, 415...) são tratadas pela classe base e
 * convertidas em {@link #handleExceptionInternal}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final ErroResponseFactory erros;

	public GlobalExceptionHandler(ErroResponseFactory erros) {
		this.erros = erros;
	}

	@ExceptionHandler(VozVivaException.class)
	ResponseEntity<ErroResponse> tratarDominio(VozVivaException ex, HttpServletRequest request) {
		CodigoErro codigo = ex.getCodigo();
		if (codigo.status().is5xxServerError()) {
			log.warn("{}: {}", codigo, paraLog(ex.getMessage()), ex);
		}
		else {
			log.info("{}: {}", codigo, paraLog(ex.getMessage()));
		}
		return responder(erros.criar(codigo, ex.getMessage(), request.getRequestURI()), null);
	}

	@ExceptionHandler(TimeoutException.class)
	ResponseEntity<ErroResponse> tratarTimeout(TimeoutException ex, HttpServletRequest request) {
		log.warn("Timeout ao acessar dependência externa", ex);
		return responder(erros.criar(CodigoErro.SERVICO_INDISPONIVEL,
				"Tempo de resposta esgotado ao acessar um serviço externo.", request.getRequestURI()), null);
	}

	/**
	 * Ordenação por um campo que não existe (ex.: {@code ?sort=senha}) é erro do cliente, não do servidor.
	 */
	@ExceptionHandler(PropertyReferenceException.class)
	ResponseEntity<ErroResponse> tratarOrdenacaoInvalida(PropertyReferenceException ex, HttpServletRequest request) {
		return responder(erros.criar(CodigoErro.REQUISICAO_INVALIDA,
				"Campo de ordenação inválido: " + ex.getPropertyName() + ".", request.getRequestURI()), null);
	}

	/**
	 * Erros de autenticação/autorização são devolvidos ao Spring Security, que decide entre
	 * 401 e 403 e responde pelos handlers de segurança no mesmo formato.
	 */
	@ExceptionHandler({ AccessDeniedException.class, AuthenticationException.class })
	void repassarParaSpringSecurity(RuntimeException ex) {
		throw ex;
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErroResponse> tratarInesperado(Exception ex, HttpServletRequest request) {
		log.error("Erro inesperado em {} {}", paraLog(request.getMethod()), paraLog(request.getRequestURI()), ex);
		return responder(erros.criar(CodigoErro.ERRO_INTERNO,
				"Ocorreu um erro inesperado. Informe o traceId ao suporte.", request.getRequestURI()), null);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
			.map(GlobalExceptionHandler::campoInvalido)
			.toList();
		return validacao(campos, headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<CampoInvalido> campos = ex.getParameterValidationResults().stream()
			.flatMap(resultado -> resultado.getResolvableErrors().stream()
				.map(erro -> new CampoInvalido(resultado.getMethodParameter().getParameterName(),
						erro.getDefaultMessage())))
			.toList();
		return validacao(campos, headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		CodigoErro codigo = CodigoErro.porStatus(statusCode.value());
		String detalhe = body instanceof ProblemDetail problema && problema.getDetail() != null
				? problema.getDetail() : codigo.titulo();
		if (statusCode.is5xxServerError()) {
			log.error("Erro {} tratado pelo Spring MVC", statusCode.value(), ex);
		}
		return ResponseEntity.status(statusCode)
			.headers(headers)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(erros.criar(codigo, detalhe, caminho(request)));
	}

	private ResponseEntity<Object> validacao(List<CampoInvalido> campos, HttpHeaders headers, WebRequest request) {
		ErroResponse erro = erros.criar(CodigoErro.VALIDACAO, "Um ou mais campos são inválidos.",
				caminho(request), campos);
		return ResponseEntity.status(erro.status())
			.headers(headers)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(erro);
	}

	private static CampoInvalido campoInvalido(FieldError erro) {
		return new CampoInvalido(erro.getField(), erro.getDefaultMessage());
	}

	private static ResponseEntity<ErroResponse> responder(ErroResponse erro, @Nullable HttpHeaders headers) {
		return ResponseEntity.status(erro.status())
			.headers(headers)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(erro);
	}

	/**
	 * Remove quebras de linha de valores vindos do cliente antes de registrá-los no log,
	 * impedindo a criação de linhas falsas (log injection).
	 */
	static String paraLog(String valor) {
		// \R cobre qualquer quebra de linha (inclusive separadores Unicode) e é reconhecido pelo CodeQL.
		return valor == null ? null : valor.replaceAll("\\R", "_");
	}

	private static String caminho(WebRequest request) {
		return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
	}

}
