package br.com.fiap.vozviva.comum.erro;

import br.com.fiap.vozviva.comum.dto.ErroResponse;
import br.com.fiap.vozviva.comum.rastreio.TraceIdFilter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Monta o {@link ErroResponse} de qualquer erro da API. Usada pelo tratador global e pelos
 * handlers de segurança (401/403), para que todos os erros tenham o mesmo contrato.
 */
@Component
public class ErroResponseFactory {

	private static final String PREFIXO_TIPO = "urn:vozviva:erro:";

	private final Clock clock;

	public ErroResponseFactory() {
		this(Clock.systemUTC());
	}

	ErroResponseFactory(Clock clock) {
		this.clock = clock;
	}

	public ErroResponse criar(CodigoErro codigo, String detalhe, String caminho) {
		return criar(codigo, detalhe, caminho, null);
	}

	public ErroResponse criar(CodigoErro codigo, String detalhe, String caminho,
			List<ErroResponse.CampoInvalido> campos) {
		return new ErroResponse(
				PREFIXO_TIPO + codigo.name().toLowerCase(Locale.ROOT),
				codigo.titulo(),
				codigo.status().value(),
				detalhe,
				caminho,
				codigo.name(),
				codigo.mensagemFalada(),
				Instant.now(clock).toString(),
				TraceIdFilter.atual(),
				campos);
	}

}
