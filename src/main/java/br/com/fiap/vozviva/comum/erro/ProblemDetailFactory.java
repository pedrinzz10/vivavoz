package br.com.fiap.vozviva.comum.erro;

import br.com.fiap.vozviva.comum.rastreio.TraceIdFilter;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Monta as respostas de erro no formato Problem Details (RFC 9457) com os campos extras
 * da API: {@code codigo}, {@code mensagemFalada}, {@code timestamp} e {@code traceId}.
 * Usada pelo tratador global e pelos handlers de segurança (401/403), para que todos os
 * erros tenham o mesmo formato.
 */
@Component
public class ProblemDetailFactory {

	private static final String PREFIXO_TIPO = "urn:vozviva:erro:";

	private final Clock clock;

	public ProblemDetailFactory() {
		this(Clock.systemUTC());
	}

	ProblemDetailFactory(Clock clock) {
		this.clock = clock;
	}

	public ProblemDetail criar(CodigoErro codigo, String detalhe, String caminho) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(codigo.status(), detalhe);
		completar(problema, codigo, caminho);
		return problema;
	}

	/**
	 * Adiciona os campos padrão a um {@link ProblemDetail} já criado pelo Spring MVC,
	 * sobrescrevendo título e tipo para os do catálogo.
	 */
	public void completar(ProblemDetail problema, CodigoErro codigo, String caminho) {
		problema.setType(URI.create(PREFIXO_TIPO + codigo.name().toLowerCase(Locale.ROOT)));
		problema.setTitle(codigo.titulo());
		if (caminho != null) {
			problema.setInstance(URI.create(caminho));
		}
		problema.setProperty("codigo", codigo.name());
		problema.setProperty("mensagemFalada", codigo.mensagemFalada());
		problema.setProperty("timestamp", Instant.now(clock).toString());
		problema.setProperty("traceId", TraceIdFilter.atual());
	}

	/**
	 * Representação plana (como o Spring MVC serializa) para escrever a resposta fora do
	 * fluxo de controllers, por exemplo nos handlers do Spring Security.
	 */
	public Map<String, Object> comoMapa(ProblemDetail problema) {
		Map<String, Object> mapa = new LinkedHashMap<>();
		mapa.put("type", problema.getType().toString());
		mapa.put("title", problema.getTitle());
		mapa.put("status", problema.getStatus());
		mapa.put("detail", problema.getDetail());
		if (problema.getInstance() != null) {
			mapa.put("instance", problema.getInstance().toString());
		}
		if (problema.getProperties() != null) {
			mapa.putAll(problema.getProperties());
		}
		return mapa;
	}

}
