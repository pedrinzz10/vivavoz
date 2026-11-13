package br.com.fiap.vozviva.seguranca;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gera tokens no formato emitido pela API .NET, assinados com o segredo de teste.
 */
public final class TokenDeTeste {

	public static final String ISSUER = "vozviva-dotnet-teste";

	private String subject = "usuario-1";
	private String issuer = ISSUER;
	private Instant expiraEm = Instant.now().plus(Duration.ofMinutes(15));
	private boolean semExpiracao;
	private String secret = SegredoJwtDeTeste.SECRET;
	private final Map<String, Object> claims = new LinkedHashMap<>();

	private TokenDeTeste() {
	}

	public static TokenDeTeste cidadao(String id) {
		return new TokenDeTeste().subject(id).claim("role", "CIDADAO");
	}

	public static TokenDeTeste cuidador(String id) {
		return new TokenDeTeste().subject(id).claim("role", "CUIDADOR");
	}

	public static TokenDeTeste semPapel(String id) {
		return new TokenDeTeste().subject(id);
	}

	public TokenDeTeste subject(String subject) {
		this.subject = subject;
		return this;
	}

	public TokenDeTeste issuer(String issuer) {
		this.issuer = issuer;
		return this;
	}

	public TokenDeTeste expiraEm(Instant expiraEm) {
		this.expiraEm = expiraEm;
		return this;
	}

	public TokenDeTeste semExpiracao() {
		this.semExpiracao = true;
		return this;
	}

	public TokenDeTeste assinadoCom(String outroSecret) {
		this.secret = outroSecret;
		return this;
	}

	public TokenDeTeste claim(String nome, Object valor) {
		claims.put(nome, valor);
		return this;
	}

	public String gerar() {
		JWTClaimsSet.Builder corpo = new JWTClaimsSet.Builder()
			.subject(subject)
			.issuer(issuer)
			.issueTime(new Date());
		if (!semExpiracao) {
			corpo.expirationTime(Date.from(expiraEm));
		}
		claims.forEach(corpo::claim);
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), corpo.build());
			jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
			return jwt.serialize();
		}
		catch (JOSEException e) {
			throw new IllegalStateException("Falha ao assinar token de teste", e);
		}
	}

	public String bearer() {
		return "Bearer " + gerar();
	}

}
