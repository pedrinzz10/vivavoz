package br.com.fiap.vozviva.seguranca;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Converte o JWT do .NET em autenticação do Spring: papéis viram ROLE_CIDADAO / ROLE_CUIDADOR
 * e o identificador do usuário vira o nome do principal.
 * <p>
 * Aceita papéis como lista ou texto (inclusive separado por vírgula ou espaço), já que cada
 * versão do ASP.NET serializa a claim de um jeito.
 */
public class PapeisJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final List<String> claimsPapel;
	private final List<String> claimsUsuario;

	public PapeisJwtConverter(JwtProperties propriedades) {
		this.claimsPapel = propriedades.claimsPapel();
		this.claimsUsuario = propriedades.claimsUsuario();
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		List<GrantedAuthority> authorities = papeis(jwt).stream()
			.map(papel -> (GrantedAuthority) new SimpleGrantedAuthority(papel.authority()))
			.toList();
		return new JwtAuthenticationToken(jwt, authorities, usuarioId(jwt));
	}

	Set<Papel> papeis(Jwt jwt) {
		Set<Papel> papeis = EnumSet.noneOf(Papel.class);
		for (String claim : claimsPapel) {
			valores(jwt.getClaims().get(claim))
				.map(Papel::deValor)
				.flatMap(Optional::stream)
				.forEach(papeis::add);
		}
		return papeis;
	}

	String usuarioId(Jwt jwt) {
		for (String claim : claimsUsuario) {
			Object valor = jwt.getClaims().get(claim);
			if (valor != null && !valor.toString().isBlank()) {
				return valor.toString();
			}
		}
		return jwt.getSubject();
	}

	private static Stream<String> valores(Object claim) {
		if (claim instanceof Collection<?> colecao) {
			return colecao.stream().filter(Objects::nonNull).map(Object::toString);
		}
		if (claim instanceof String texto) {
			return Stream.of(texto.split("[,\\s]+"));
		}
		return Stream.empty();
	}

}
