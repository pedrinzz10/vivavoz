package br.com.fiap.vozviva.perfil;

import br.com.fiap.vozviva.comum.dto.PaginaResponse;
import br.com.fiap.vozviva.perfil.dto.ContatoEmergenciaRequest;
import br.com.fiap.vozviva.perfil.dto.ContatoEmergenciaResponse;
import br.com.fiap.vozviva.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/v1/perfis-emergencia/{perfilId}/contatos")
public class ContatoEmergenciaController {

	private final PerfilEmergenciaService perfis;
	private final UsuarioAutenticado usuario;

	public ContatoEmergenciaController(PerfilEmergenciaService perfis, UsuarioAutenticado usuario) {
		this.perfis = perfis;
		this.usuario = usuario;
	}

	@PostMapping
	@PreAuthorize("hasRole('CUIDADOR')")
	public ResponseEntity<ContatoEmergenciaResponse> adicionar(@PathVariable Long perfilId,
			@Valid @RequestBody ContatoEmergenciaRequest request) {
		ContatoEmergenciaResponse criado = perfis.adicionarContato(perfilId, usuario.idNumerico(), request);
		URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
		return ResponseEntity.created(local).body(criado);
	}

	/** Contatos do perfil. Ordenação padrão: prioridade (1 primeiro). */
	@GetMapping
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public PaginaResponse<ContatoEmergenciaResponse> listar(@PathVariable Long perfilId,
			@PageableDefault(size = 10, sort = "prioridade") Pageable pageable) {
		return perfis.listarContatos(perfilId, usuario.idNumerico(), pageable);
	}

	@GetMapping("/{contatoId}")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public ContatoEmergenciaResponse buscar(@PathVariable Long perfilId, @PathVariable Long contatoId) {
		return perfis.buscarContato(perfilId, contatoId, usuario.idNumerico());
	}

	@PutMapping("/{contatoId}")
	@PreAuthorize("hasRole('CUIDADOR')")
	public ContatoEmergenciaResponse atualizar(@PathVariable Long perfilId, @PathVariable Long contatoId,
			@Valid @RequestBody ContatoEmergenciaRequest request) {
		return perfis.atualizarContato(perfilId, contatoId, usuario.idNumerico(), request);
	}

	@DeleteMapping("/{contatoId}")
	@PreAuthorize("hasRole('CUIDADOR')")
	public ResponseEntity<Void> excluir(@PathVariable Long perfilId, @PathVariable Long contatoId) {
		perfis.excluirContato(perfilId, contatoId, usuario.idNumerico());
		return ResponseEntity.noContent().build();
	}

}
