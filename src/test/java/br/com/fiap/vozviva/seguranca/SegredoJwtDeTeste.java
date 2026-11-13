package br.com.fiap.vozviva.seguranca;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Gera um segredo JWT aleatório por execução dos testes, para que nenhum segredo fique
 * escrito no repositório. Registrado em META-INF/spring.factories (somente no classpath de teste).
 */
public class SegredoJwtDeTeste implements EnvironmentPostProcessor {

	public static final String PROPRIEDADE = "vozviva.seguranca.jwt.secret";

	/** Mesmo valor para todos os contextos Spring da mesma JVM de testes. */
	public static final String SECRET = gerar();

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		environment.getPropertySources().addFirst(new MapPropertySource("segredo-jwt-de-teste", Map.of(PROPRIEDADE, SECRET)));
	}

	private static String gerar() {
		byte[] bytes = new byte[48];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
