package br.com.fiap.vozviva.comum.rastreio;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Atribui um traceId a cada requisição, expõe no header {@value #HEADER} e no MDC do log,
 * permitindo correlacionar a resposta de erro recebida pelo app com a linha de log no servidor.
 * Roda antes do Spring Security para que respostas 401/403 também tenham traceId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Trace-Id";
	public static final String MDC_KEY = "traceId";

	// Aceita o traceId do cliente apenas em formato seguro, evitando injeção de conteúdo no log.
	private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String traceId = resolverTraceId(request.getHeader(HEADER));
		MDC.put(MDC_KEY, traceId);
		response.setHeader(HEADER, traceId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	private static String resolverTraceId(String recebido) {
		if (recebido != null && FORMATO_VALIDO.matcher(recebido).matches()) {
			return recebido;
		}
		return UUID.randomUUID().toString();
	}

	/**
	 * traceId da requisição atual, ou {@code null} fora de uma requisição.
	 */
	public static String atual() {
		return MDC.get(MDC_KEY);
	}

}
