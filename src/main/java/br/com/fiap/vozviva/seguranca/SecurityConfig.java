package br.com.fiap.vozviva.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A API Java é um Resource Server: valida o JWT emitido pela API .NET, sem emitir tokens.
 * <p>
 * Matriz de acesso: health/info e documentação são públicos; todo o resto exige token válido.
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

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler erros,
			JwtProperties propriedades) throws Exception {
		http
			// API stateless com Bearer token: sem sessão, sem cookie, logo sem CSRF.
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(rotas -> rotas
				.requestMatchers(HttpMethod.GET, ROTAS_PUBLICAS).permitAll()
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

	@Bean
	JwtDecoder jwtDecoder(JwtProperties propriedades) {
		return criarDecoder(propriedades);
	}

	static JwtDecoder criarDecoder(JwtProperties propriedades) {
		NimbusJwtDecoder decoder;
		if (propriedades.usaJwks()) {
			decoder = NimbusJwtDecoder.withJwkSetUri(propriedades.jwksUri()).build();
		}
		else if (propriedades.temSecret()) {
			byte[] chave = propriedades.secret().getBytes(StandardCharsets.UTF_8);
			if (chave.length < TAMANHO_MINIMO_SECRET) {
				throw new IllegalStateException("JWT_SECRET deve ter pelo menos " + TAMANHO_MINIMO_SECRET
						+ " bytes (256 bits). Use o mesmo segredo configurado na API .NET.");
			}
			MacAlgorithm algoritmo = MacAlgorithm.from(propriedades.algoritmo());
			if (algoritmo == null) {
				throw new IllegalStateException("Algoritmo JWT não suportado: " + propriedades.algoritmo()
						+ ". Use HS256, HS384 ou HS512.");
			}
			decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(chave, algoritmo.getName()))
				.macAlgorithm(algoritmo)
				.build();
		}
		else {
			throw new IllegalStateException(
					"Configure JWT_SECRET (segredo compartilhado com a API .NET) ou JWT_JWKS_URI.");
		}
		decoder.setJwtValidator(validador(propriedades));
		return decoder;
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
