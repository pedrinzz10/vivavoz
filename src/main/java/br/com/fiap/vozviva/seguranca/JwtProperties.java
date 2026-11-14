package br.com.fiap.vozviva.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * Configuração da validação do JWT emitido pela API .NET.
 *
 * @param secret            segredo compartilhado com o .NET (HMAC); mínimo de 32 bytes
 * @param jwksUri           alternativa ao segredo: endpoint JWKS do .NET (chave assimétrica)
 * @param algoritmo         algoritmo HMAC usado com o segredo (HS256, HS384 ou HS512)
 * @param issuer            emissor esperado (claim iss); vazio desativa a verificação
 * @param audience          audiência esperada (claim aud); vazio desativa a verificação
 * @param claimsPapel       claims onde procurar os papéis, em ordem de prioridade
 * @param claimsUsuario     claims onde procurar o identificador do usuário, em ordem de prioridade
 * @param toleranciaRelogio diferença de relógio aceita entre os servidores
 */
@ConfigurationProperties("vozviva.seguranca.jwt")
public record JwtProperties(
		String secret,
		String jwksUri,
		String algoritmo,
		String issuer,
		String audience,
		List<String> claimsPapel,
		List<String> claimsUsuario,
		Duration toleranciaRelogio) {

	/** Claim de papel usada pelo ASP.NET (ClaimTypes.Role) quando não há mapeamento para "role". */
	public static final String CLAIM_PAPEL_DOTNET = "http://schemas.microsoft.com/ws/2008/06/identity/claims/role";

	/** Claim de identificador usada pelo ASP.NET (ClaimTypes.NameIdentifier). */
	public static final String CLAIM_USUARIO_DOTNET = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier";

	public JwtProperties {
		algoritmo = StringUtils.hasText(algoritmo) ? algoritmo : "HS256";
		claimsPapel = claimsPapel == null || claimsPapel.isEmpty()
				? List.of("roles", "role", CLAIM_PAPEL_DOTNET) : List.copyOf(claimsPapel);
		claimsUsuario = claimsUsuario == null || claimsUsuario.isEmpty()
				? List.of("sub", "nameid", CLAIM_USUARIO_DOTNET) : List.copyOf(claimsUsuario);
		toleranciaRelogio = toleranciaRelogio == null ? Duration.ofSeconds(30) : toleranciaRelogio;
	}

	public boolean usaJwks() {
		return StringUtils.hasText(jwksUri);
	}

	public boolean temSecret() {
		return StringUtils.hasText(secret);
	}

}
