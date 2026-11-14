package br.com.fiap.vozviva.seguranca;

import br.com.fiap.vozviva.comum.erro.CodigoErro;
import br.com.fiap.vozviva.comum.dto.ErroResponse;
import br.com.fiap.vozviva.comum.erro.ErroResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Responde 401 e 403 do Spring Security com o mesmo {@link ErroResponse} usado pela API.
 * Deve ser registrado na SecurityFilterChain como entry point e access denied handler.
 */
@Component
public class ErroSegurancaHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ErroResponseFactory erros;
	private final ObjectMapper objectMapper;

	public ErroSegurancaHandler(ErroResponseFactory erros, ObjectMapper objectMapper) {
		this.erros = erros;
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException ex) throws IOException {
		escrever(request, response, CodigoErro.NAO_AUTENTICADO,
				"Credenciais ausentes, inválidas ou expiradas.");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException ex) throws IOException {
		// O filtro de CSRF roda antes da autenticação: uma escrita sem token JWT falha no CSRF.
		// Para o app, isso significa "precisa entrar de novo" (401), não "sem permissão" (403).
		if (ex instanceof CsrfException && naoAutenticado()) {
			commence(request, response, null);
			return;
		}
		escrever(request, response, CodigoErro.ACESSO_NEGADO,
				"O usuário não tem permissão para acessar este recurso.");
	}

	private static boolean naoAutenticado() {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		return autenticacao == null || autenticacao instanceof AnonymousAuthenticationToken
				|| !autenticacao.isAuthenticated();
	}

	private void escrever(HttpServletRequest request, HttpServletResponse response, CodigoErro codigo,
			String detalhe) throws IOException {
		ErroResponse erro = erros.criar(codigo, detalhe, request.getRequestURI());
		response.setStatus(codigo.status().value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getOutputStream(), erro);
	}

}
