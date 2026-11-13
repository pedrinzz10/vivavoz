package br.com.fiap.vozviva.seguranca;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
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
	Map<String, Object> eu() {
		Set<Papel> papeis = usuario.papeis();
		return Map.of("id", usuario.id(), "papeis", papeis);
	}

	@GetMapping("/suporte")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	Map<String, String> suporte() {
		return Map.of("dados", "suporte");
	}

	@PutMapping("/perfis/{cidadaoId}")
	@PreAuthorize("hasRole('CUIDADOR')")
	Map<String, String> editarPerfil(@PathVariable String cidadaoId) {
		return Map.of("cidadaoId", cidadaoId, "situacao", "atualizado");
	}

}
