package br.com.fiap.vozviva.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Avisa a API .NET de zeladoria quando o cidadão exclui a conta (LGPD).
 * <p>
 * As duas APIs aceitam o mesmo token, mas cada uma tem o seu banco. Sem o aviso, a zeladoria continuaria aceitando o
 * token até ele expirar e guardaria os reportes da pessoa. A chamada usa o próprio token de quem está excluindo a
 * conta, então só funciona para os dados dele. Se a zeladoria estiver fora do ar, a exclusão aqui segue mesmo assim e
 * o aviso fica no log: a conta não pode ficar presa por causa de outro sistema.
 */
@Service
public class ZeladoriaCliente {

	private static final Logger log = LoggerFactory.getLogger(ZeladoriaCliente.class);
	private static final Duration TEMPO_MAXIMO = Duration.ofSeconds(5);

	private final RestClient cliente;
	private final boolean configurada;

	public ZeladoriaCliente(RestClient.Builder builder, @Value("${vozviva.zeladoria.url:}") String url) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(TEMPO_MAXIMO);
		fabrica.setReadTimeout(TEMPO_MAXIMO);
		this.configurada = StringUtils.hasText(url);
		this.cliente = builder.baseUrl(configurada ? url.strip() : "http://localhost").requestFactory(fabrica).build();
	}

	/** Pede à zeladoria que apague os dados da pessoa e passe a recusar o token dela. */
	public void excluirConta(String token) {
		if (!configurada || !StringUtils.hasText(token)) {
			return;
		}
		try {
			cliente.delete()
				.uri("/api/usuarios/eu")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientException ex) {
			log.warn("Não foi possível avisar a API de zeladoria da exclusão da conta: {}", ex.getClass().getSimpleName());
		}
	}

}
