package br.com.fiap.vozviva.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class EmissorTokenTest {

	private static final Instant AGORA = Instant.now();

	private final JwtProperties propriedades = new JwtProperties(SegredoJwtDeTeste.SECRET, null, TokenDeTeste.ISSUER,
			"vozviva-api", Duration.ofHours(2), null, null, null);

	private final EmissorToken emissor = new EmissorToken(SecurityConfig.criarEncoder(propriedades), propriedades,
			Clock.fixed(AGORA, ZoneOffset.UTC));

	@Test
	void tokenEmitidoTemOContratoCombinadoComADotNet() {
		EmissorToken.TokenEmitido emitido = emissor.emitir("42", "Maria", Papel.CUIDADOR);

		JwtDecoder decoder = SecurityConfig.criarDecoder(propriedades);
		Jwt jwt = decoder.decode(emitido.valor());

		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(jwt.getClaimAsString("role")).isEqualTo("CUIDADOR");
		assertThat(jwt.getClaimAsString("name")).isEqualTo("Maria");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo(TokenDeTeste.ISSUER);
		assertThat(jwt.getAudience()).containsExactly("vozviva-api");
		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
	}

	@Test
	void expiraConformeConfiguracao() {
		EmissorToken.TokenEmitido emitido = emissor.emitir("1", "João", Papel.CIDADAO);

		assertThat(emitido.expiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(2)));
	}

	@Test
	void tokenEmitidoViraAutenticacaoComOPapelCerto() {
		EmissorToken.TokenEmitido emitido = emissor.emitir("7", "Ana", Papel.CIDADAO);
		Jwt jwt = SecurityConfig.criarDecoder(propriedades).decode(emitido.valor());

		var autenticacao = new PapeisJwtConverter(propriedades).convert(jwt);

		assertThat(autenticacao.getName()).isEqualTo("7");
		assertThat(autenticacao.getAuthorities()).extracting("authority").containsExactly("ROLE_CIDADAO");
	}

}
