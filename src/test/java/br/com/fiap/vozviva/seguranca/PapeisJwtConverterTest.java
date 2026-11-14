package br.com.fiap.vozviva.seguranca;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PapeisJwtConverterTest {

	private final PapeisJwtConverter converter = new PapeisJwtConverter(
			new JwtProperties(null, null, null, null, null, null, null, null));

	static Stream<Arguments> formatosDePapel() {
		return Stream.of(
				Arguments.of("roles", List.of("CIDADAO"), Set.of(Papel.CIDADAO)),
				Arguments.of("role", "cuidador", Set.of(Papel.CUIDADOR)),
				Arguments.of("role", "ROLE_CIDADAO", Set.of(Papel.CIDADAO)),
				Arguments.of(JwtProperties.CLAIM_PAPEL_DOTNET, "CUIDADOR", Set.of(Papel.CUIDADOR)),
				Arguments.of("roles", "CIDADAO,CUIDADOR", Set.of(Papel.CIDADAO, Papel.CUIDADOR)),
				Arguments.of("roles", "CIDADAO CUIDADOR", Set.of(Papel.CIDADAO, Papel.CUIDADOR)),
				Arguments.of("roles", List.of("ADMIN", "CIDADAO"), Set.of(Papel.CIDADAO)),
				Arguments.of("role", "ADMIN", Set.of()),
				Arguments.of("scope", "CUIDADOR", Set.of()));
	}

	@ParameterizedTest
	@MethodSource("formatosDePapel")
	void extraiPapeisConhecidos(String claim, Object valor, Set<Papel> esperados) {
		Jwt jwt = jwt().claim(claim, valor).build();

		assertThat(converter.papeis(jwt)).containsExactlyInAnyOrderElementsOf(esperados);
		assertThat(converter.convert(jwt).getAuthorities())
			.extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrderElementsOf(esperados.stream().map(Papel::authority).toList());
	}

	@Test
	void usaSubComoIdentificador() {
		Jwt jwt = jwt().subject("abc-123").build();

		assertThat(converter.convert(jwt).getName()).isEqualTo("abc-123");
	}

	@Test
	void usaNameIdentifierDoDotnetQuandoNaoHaSub() {
		Jwt jwt = jwt().claim(JwtProperties.CLAIM_USUARIO_DOTNET, "77").build();

		assertThat(converter.convert(jwt).getName()).isEqualTo("77");
	}

	private static Jwt.Builder jwt() {
		return Jwt.withTokenValue("token").header("alg", "HS256");
	}

}
