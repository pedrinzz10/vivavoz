package br.com.fiap.vozviva.seguranca;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Rotas usadas apenas nos testes para exercitar a matriz de acesso.
 */
@RestController
@RequestMapping("/teste-seguranca")
class SegurancaTesteController {

	private final UsuarioAutenticado usuario;

	SegurancaTesteController(UsuarioAutenticado usuario) {
		this.usuario = usuario;
	}

	@GetMapping("/eu")
	UsuarioResponse eu() {
		return new UsuarioResponse(usuario.id(), usuario.papeis());
	}

	@GetMapping("/suporte")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	SuporteResponse suporte() {
		return new SuporteResponse("suporte");
	}

	@PutMapping("/perfis/{cidadaoId}")
	@PreAuthorize("hasRole('CUIDADOR')")
	PerfilAtualizadoResponse editarPerfil(@PathVariable String cidadaoId) {
		return new PerfilAtualizadoResponse(cidadaoId, "atualizado");
	}

	record UsuarioResponse(String id, Set<Papel> papeis) {
	}

	record SuporteResponse(String dados) {
	}

	record PerfilAtualizadoResponse(String cidadaoId, String situacao) {
	}

}
