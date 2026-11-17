package br.com.fiap.vozviva.security;

import br.com.fiap.vozviva.config.JwtProperties;
import br.com.fiap.vozviva.model.AreaOrgao;
import br.com.fiap.vozviva.model.Papel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Emite o JWT do ecossistema. O formato (sub, role, name, iss, iat, exp, assinatura HMAC) é o
 * contrato que a API .NET usa para validar o mesmo token.
 */
@Component
public class EmissorToken {

	private final JwtEncoder encoder;
	private final JwtProperties propriedades;
	private final Clock clock;

	@Autowired
	public EmissorToken(JwtEncoder encoder, JwtProperties propriedades) {
		this(encoder, propriedades, Clock.systemUTC());
	}

	EmissorToken(JwtEncoder encoder, JwtProperties propriedades, Clock clock) {
		this.encoder = encoder;
		this.propriedades = propriedades;
		this.clock = clock;
	}

	public TokenEmitido emitir(String usuarioId, String nome, Papel papel) {
		return emitir(usuarioId, nome, papel, null);
	}

	/**
	 * @param orgao órgão do gestor; vai na claim {@code orgao}, que a API .NET usa para montar a fila de
	 *              reportes. Para os outros papéis é {@code null} e a claim não existe.
	 */
	public TokenEmitido emitir(String usuarioId, String nome, Papel papel, AreaOrgao orgao) {
		Instant agora = Instant.now(clock);
		Instant expiraEm = agora.plus(propriedades.expiracao());
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.subject(usuarioId)
			.claim("role", papel.name())
			.claim("name", nome)
			.issuedAt(agora)
			.expiresAt(expiraEm);
		if (orgao != null) {
			claims.claim("orgao", orgao.name());
		}
		if (StringUtils.hasText(propriedades.issuer())) {
			claims.issuer(propriedades.issuer());
		}
		if (StringUtils.hasText(propriedades.audience())) {
			claims.audience(List.of(propriedades.audience()));
		}
		JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.from(propriedades.algoritmo())).type("JWT").build();
		String valor = encoder.encode(JwtEncoderParameters.from(cabecalho, claims.build())).getTokenValue();
		return new TokenEmitido(valor, expiraEm);
	}

	public record TokenEmitido(String valor, Instant expiraEm) {
	}

}
