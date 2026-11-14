package br.com.fiap.vozviva.perfil;

import br.com.fiap.vozviva.comum.dto.PaginaResponse;
import br.com.fiap.vozviva.perfil.dto.AtualizarPerfilEmergenciaRequest;
import br.com.fiap.vozviva.perfil.dto.PerfilEmergenciaRequest;
import br.com.fiap.vozviva.perfil.dto.PerfilEmergenciaResponse;
import br.com.fiap.vozviva.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/perfis-emergencia")
public class PerfilEmergenciaController {

	private final PerfilEmergenciaService perfis;
	private final UsuarioAutenticado usuario;

	public PerfilEmergenciaController(PerfilEmergenciaService perfis, UsuarioAutenticado usuario) {
		this.perfis = perfis;
		this.usuario = usuario;
	}

	@PostMapping
	@PreAuthorize("hasRole('CUIDADOR')")
	public ResponseEntity<PerfilEmergenciaResponse> criar(@Valid @RequestBody PerfilEmergenciaRequest request) {
		PerfilEmergenciaResponse criado = perfis.criar(usuario.idNumerico(), request);
		URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
		return ResponseEntity.created(local).body(criado);
	}

	/** Perfis que o cuidador gerencia. Ordenação padrão: mais recentes primeiro. */
	@GetMapping
	@PreAuthorize("hasRole('CUIDADOR')")
	public PaginaResponse<PerfilEmergenciaResponse> listar(
			@PageableDefault(size = 10, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
		return perfis.listarDoCuidador(usuario.idNumerico(), pageable);
	}

	@GetMapping("/meu")
	@PreAuthorize("hasRole('CIDADAO')")
	public PerfilEmergenciaResponse meu() {
		return perfis.buscarDoCidadao(usuario.idNumerico());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public PerfilEmergenciaResponse buscar(@PathVariable Long id) {
		return perfis.buscar(id, usuario.idNumerico());
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('CUIDADOR')")
	public PerfilEmergenciaResponse atualizar(@PathVariable Long id,
			@Valid @RequestBody AtualizarPerfilEmergenciaRequest request) {
		return perfis.atualizar(id, usuario.idNumerico(), request);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('CUIDADOR')")
	public ResponseEntity<Void> excluir(@PathVariable Long id) {
		perfis.excluir(id, usuario.idNumerico());
		return ResponseEntity.noContent().build();
	}

}
