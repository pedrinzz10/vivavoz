package br.com.fiap.vozviva.usuario.dto;

/**
 * Resposta do login: o token vai no header {@code Authorization: Bearer <accessToken>} das próximas chamadas,
 * tanto na API Java quanto na API .NET.
 *
 * @param expiraEm momento de expiração do token (ISO-8601, UTC)
 */
public record TokenResponse(String accessToken, String tokenType, String expiraEm, UsuarioResponse usuario) {
}
