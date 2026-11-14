package br.com.fiap.vozviva.seguranca;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Acesso ao usuário da requisição atual para os serviços, sem depender de detalhes do JWT.
 */
@Component
public class UsuarioAutenticado {

	/**
	 * Identificador do usuário (claim sub ou equivalente).
	 */
	public String id() {
		return autenticacao().getName();
	}

	/**
	 * Identificador numérico do usuário (tokens emitidos por esta API). Um token com {@code sub}
	 * não numérico é tratado como credencial inválida (401).
	 */
	public Long idNumerico() {
		try {
			return Long.valueOf(id());
		}
		catch (NumberFormatException ex) {
			throw new BadCredentialsException("Identificador de usuário inválido no token.");
		}
	}

	public Set<Papel> papeis() {
		Set<Papel> papeis = EnumSet.noneOf(Papel.class);
		for (GrantedAuthority authority : autenticacao().getAuthorities()) {
			Papel.deValor(authority.getAuthority()).ifPresent(papeis::add);
		}
		return papeis;
	}

	public boolean possui(Papel papel) {
		return papeis().contains(papel);
	}

	/**
	 * Token original, para repassar à API .NET nas chamadas de integração (SPEC-011).
	 */
	public String token() {
		if (autenticacao() instanceof JwtAuthenticationToken jwt) {
			return jwt.getToken().getTokenValue();
		}
		throw new AuthenticationCredentialsNotFoundException("Requisição sem token JWT.");
	}

	private static Authentication autenticacao() {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao == null || !autenticacao.isAuthenticated()) {
			throw new AuthenticationCredentialsNotFoundException("Nenhum usuário autenticado.");
		}
		return autenticacao;
	}

}
