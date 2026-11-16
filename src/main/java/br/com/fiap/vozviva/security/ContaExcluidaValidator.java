package br.com.fiap.vozviva.security;

import br.com.fiap.vozviva.service.ContasExcluidas;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Recusa o token de uma conta excluída, mesmo antes de ele expirar: depois do {@code DELETE /api/v1/auth/eu},
 * nenhuma rota aceita mais as credenciais dessa conta (401).
 */
public class ContaExcluidaValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error CONTA_EXCLUIDA = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
			"A conta deste token foi excluída.", null);

	private final ContasExcluidas contasExcluidas;

	public ContaExcluidaValidator(ContasExcluidas contasExcluidas) {
		this.contasExcluidas = contasExcluidas;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		Long usuarioId = idNumerico(token.getSubject());
		if (usuarioId != null && contasExcluidas.foiExcluida(usuarioId)) {
			return OAuth2TokenValidatorResult.failure(CONTA_EXCLUIDA);
		}
		return OAuth2TokenValidatorResult.success();
	}

	private static Long idNumerico(String subject) {
		try {
			return subject == null ? null : Long.valueOf(subject);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
