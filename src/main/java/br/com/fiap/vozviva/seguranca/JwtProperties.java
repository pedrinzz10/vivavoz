package br.com.fiap.vozviva.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * Configuração do JWT. A API Java emite e valida os tokens com um segredo HMAC compartilhado
 * com a API .NET, que valida o mesmo token.
 *
 * @param secret            segredo HMAC; mínimo de 32 bytes
 * @param algoritmo         algoritmo HMAC (HS256, HS384 ou HS512)
 * @param issuer            emissor gravado e exigido no token (claim iss); vazio desativa a verificação
 * @param audience          audiência exigida no token (claim aud); vazio desativa a verificação
 * @param expiracao         validade dos tokens emitidos no login
 * @param claimsPapel       claims onde procurar os papéis, em ordem de prioridade
 * @param claimsUsuario     claims onde procurar o identificador do usuário, em ordem de prioridade
 * @param toleranciaRelogio diferença de relógio aceita entre os servidores
 */
@ConfigurationProperties("vozviva.seguranca.jwt")
public record JwtProperties(
		String secret,
		String algoritmo,
		String issuer,
		String audience,
		Duration expiracao,
		List<String> claimsPapel,
		List<String> claimsUsuario,
		Duration toleranciaRelogio) {

	/** Claim de papel usada pelo ASP.NET (ClaimTypes.Role) quando não há mapeamento para "role". */
	public static final String CLAIM_PAPEL_DOTNET = "http://schemas.microsoft.com/ws/2008/06/identity/claims/role";

	/** Claim de identificador usada pelo ASP.NET (ClaimTypes.NameIdentifier). */
	public static final String CLAIM_USUARIO_DOTNET = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier";

	public JwtProperties {
		algoritmo = StringUtils.hasText(algoritmo) ? algoritmo : "HS256";
		expiracao = expiracao == null ? Duration.ofHours(2) : expiracao;
		claimsPapel = claimsPapel == null || claimsPapel.isEmpty()
				? List.of("roles", "role", CLAIM_PAPEL_DOTNET) : List.copyOf(claimsPapel);
		claimsUsuario = claimsUsuario == null || claimsUsuario.isEmpty()
				? List.of("sub", "nameid", CLAIM_USUARIO_DOTNET) : List.copyOf(claimsUsuario);
		toleranciaRelogio = toleranciaRelogio == null ? Duration.ofSeconds(30) : toleranciaRelogio;
	}

}
