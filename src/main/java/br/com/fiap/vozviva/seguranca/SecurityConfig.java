package br.com.fiap.vozviva.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A API Java é a dona da identidade: emite o JWT no login ({@link EmissorToken}) e valida o token em
 * todas as chamadas (Resource Server). A API .NET valida o mesmo token com o mesmo segredo.
 * <p>
 * Matriz de acesso: health/info, documentação, cadastro e login são públicos; todo o resto exige token válido.
 * Regras por papel ficam nos endpoints com {@code @PreAuthorize} (ex.: {@code hasRole('CUIDADOR')}).
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	/** Tamanho mínimo do segredo HMAC (256 bits), exigido pelo HS256. */
	static final int TAMANHO_MINIMO_SECRET = 32;

	private static final String[] ROTAS_PUBLICAS = {
			"/actuator/health", "/actuator/health/**", "/actuator/info",
			"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
	};

	/** Cadastro e login: públicos e sem CSRF (devolvem o token no corpo, que outro site não consegue ler). */
	static final String[] ROTAS_AUTENTICACAO = { "/api/v1/auth/cadastro", "/api/v1/auth/login" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ErroSegurancaHandler erros,
			JwtProperties propriedades) throws Exception {
		http
			// CSRF só explora credenciais enviadas automaticamente pelo navegador (cookies).
			// Requisições com header Authorization não podem ser forjadas por outro site, então
			// dispensam o token CSRF. As demais continuam protegidas, o que já cobre um eventual
			// login por cookie no futuro. O token fica em cookie para a API seguir sem sessão.
			.csrf(csrf -> csrf
				.ignoringRequestMatchers(new RequestHeaderRequestMatcher(HttpHeaders.AUTHORIZATION))
				.ignoringRequestMatchers(ROTAS_AUTENTICACAO)
				.csrfTokenRepository(repositorioCsrf()))
			.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(rotas -> rotas
				.requestMatchers(HttpMethod.GET, ROTAS_PUBLICAS).permitAll()
				.requestMatchers(HttpMethod.POST, ROTAS_AUTENTICACAO).permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(recurso -> recurso
				.jwt(jwt -> jwt.jwtAuthenticationConverter(new PapeisJwtConverter(propriedades)))
				.authenticationEntryPoint(erros)
				.accessDeniedHandler(erros))
			.exceptionHandling(excecoes -> excecoes
				.authenticationEntryPoint(erros)
				.accessDeniedHandler(erros));
		return http.build();
	}

	private static CookieCsrfTokenRepository repositorioCsrf() {
		CookieCsrfTokenRepository repositorio = new CookieCsrfTokenRepository();
		repositorio.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict"));
		return repositorio;
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties propriedades) {
		return criarDecoder(propriedades);
	}

	@Bean
	JwtEncoder jwtEncoder(JwtProperties propriedades) {
		return criarEncoder(propriedades);
	}

	/**
	 * Senhas com BCrypt. O formato delegado ({bcrypt}...) permite trocar o algoritmo no futuro
	 * sem invalidar as senhas já cadastradas.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	static JwtDecoder criarDecoder(JwtProperties propriedades) {
		MacAlgorithm algoritmo = algoritmo(propriedades);
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave(propriedades, algoritmo))
			.macAlgorithm(algoritmo)
			.build();
		decoder.setJwtValidator(validador(propriedades));
		return decoder;
	}

	static JwtEncoder criarEncoder(JwtProperties propriedades) {
		MacAlgorithm algoritmo = algoritmo(propriedades);
		return NimbusJwtEncoder.withSecretKey(chave(propriedades, algoritmo)).algorithm(algoritmo).build();
	}

	private static MacAlgorithm algoritmo(JwtProperties propriedades) {
		MacAlgorithm algoritmo = MacAlgorithm.from(propriedades.algoritmo());
		if (algoritmo == null) {
			throw new IllegalStateException("Algoritmo JWT não suportado: " + propriedades.algoritmo()
					+ ". Use HS256, HS384 ou HS512.");
		}
		return algoritmo;
	}

	private static SecretKey chave(JwtProperties propriedades, MacAlgorithm algoritmo) {
		if (!StringUtils.hasText(propriedades.secret())) {
			throw new IllegalStateException("Configure JWT_SECRET: segredo usado para assinar e validar os tokens.");
		}
		byte[] bytes = propriedades.secret().getBytes(StandardCharsets.UTF_8);
		if (bytes.length < TAMANHO_MINIMO_SECRET) {
			throw new IllegalStateException("JWT_SECRET deve ter pelo menos " + TAMANHO_MINIMO_SECRET
					+ " bytes (256 bits). A API .NET deve usar o mesmo segredo.");
		}
		return new SecretKeySpec(bytes, algoritmo.getName());
	}

	private static OAuth2TokenValidator<Jwt> validador(JwtProperties propriedades) {
		List<OAuth2TokenValidator<Jwt>> validadores = new ArrayList<>();
		validadores.add(new JwtTimestampValidator(propriedades.toleranciaRelogio()));
		// Tokens sem expiração nunca são aceitos.
		validadores.add(new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull));
		if (StringUtils.hasText(propriedades.issuer())) {
			validadores.add(new JwtIssuerValidator(propriedades.issuer()));
		}
		else {
			log.warn("JWT_ISSUER não configurado: o emissor do token não será verificado.");
		}
		if (StringUtils.hasText(propriedades.audience())) {
			validadores.add(new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
					aud -> aud != null && aud.contains(propriedades.audience())));
		}
		return new DelegatingOAuth2TokenValidator<>(validadores);
	}

}
