package br.com.fiap.vozviva.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

	@Test
	void falhaAoIniciarSemSecretNemJwks() {
		assertThatThrownBy(() -> SecurityConfig.criarDecoder(propriedades(null, null, null)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void falhaComSecretCurto() {
		assertThatThrownBy(() -> SecurityConfig.criarDecoder(propriedades("curto-demais", null, null)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("32 bytes");
	}

	@Test
	void falhaComAlgoritmoNaoSuportado() {
		JwtProperties props = new JwtProperties(SegredoJwtDeTeste.SECRET, null, "RS256", null, null, null, null, null);

		assertThatThrownBy(() -> SecurityConfig.criarDecoder(props))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("HS256");
	}

	@Test
	void decodificaTokenValido() {
		JwtDecoder decoder = SecurityConfig.criarDecoder(propriedades(SegredoJwtDeTeste.SECRET, TokenDeTeste.ISSUER, null));

		Jwt jwt = decoder.decode(TokenDeTeste.cidadao("u1").gerar());

		assertThat(jwt.getSubject()).isEqualTo("u1");
	}

	@Test
	void rejeitaAudienciaDiferente() {
		JwtDecoder decoder = SecurityConfig.criarDecoder(propriedades(SegredoJwtDeTeste.SECRET, null, "vozviva-api"));
		String token = TokenDeTeste.cidadao("u1").claim("aud", "outra-api").gerar();

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
	}

	@Test
	void aceitaAudienciaEsperada() {
		JwtDecoder decoder = SecurityConfig.criarDecoder(propriedades(SegredoJwtDeTeste.SECRET, null, "vozviva-api"));

		Jwt jwt = decoder.decode(TokenDeTeste.cidadao("u1").claim("aud", "vozviva-api").gerar());

		assertThat(jwt.getAudience()).contains("vozviva-api");
	}

	private static JwtProperties propriedades(String secret, String issuer, String audience) {
		return new JwtProperties(secret, null, null, issuer, audience, null, null, null);
	}

}
