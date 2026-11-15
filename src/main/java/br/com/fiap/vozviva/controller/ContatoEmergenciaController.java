package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.config.OpenApiConfig;
import br.com.fiap.vozviva.dto.ContatoEmergenciaRequest;
import br.com.fiap.vozviva.dto.ContatoEmergenciaResponse;
import br.com.fiap.vozviva.dto.PaginaResponse;
import br.com.fiap.vozviva.security.UsuarioAutenticado;
import br.com.fiap.vozviva.service.PerfilEmergenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = OpenApiConfig.TAG_CONTATOS)
public class ContatoEmergenciaController {

	private final PerfilEmergenciaService perfis;
	private final UsuarioAutenticado usuario;

	public ContatoEmergenciaController(PerfilEmergenciaService perfis, UsuarioAutenticado usuario) {
		this.perfis = perfis;
		this.usuario = usuario;
	}

	@PostMapping
	@ApiResponse(responseCode = "409", description = "Limite de 10 contatos atingido")
	@ApiResponse(responseCode = "201", description = "Contato criado (header Location aponta para ele)")
	@PreAuthorize("hasRole('CUIDADOR')")
	@Operation(summary = "Adicionar contato", description = "Até 10 contatos por perfil. O telefone é normalizado para "
			+ "+55DDDNUMERO. A prioridade (1 a 10) define a ordem de aviso no socorro.")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "filha",
			value = """
					{"nome": "Ana Souza", "telefone": "(11) 91234-5678", "parentesco": "filha", "prioridade": 1}""")))
	public ResponseEntity<ContatoEmergenciaResponse> adicionar(@PathVariable Long perfilId,
			@Valid @RequestBody ContatoEmergenciaRequest request) {
		ContatoEmergenciaResponse criado = perfis.adicionarContato(perfilId, usuario.idNumerico(), request);
		URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
		return ResponseEntity.created(local).body(criado);
	}

	/** Contatos do perfil. Ordenação padrão: prioridade (1 primeiro). */
	@GetMapping
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Listar contatos", description = "Paginado; ordenação por prioridade (padrão), nome ou id.")
	public PaginaResponse<ContatoEmergenciaResponse> listar(@PathVariable Long perfilId,
			@ParameterObject @PageableDefault(size = 10, sort = "prioridade") Pageable pageable) {
		return perfis.listarContatos(perfilId, usuario.idNumerico(), pageable);
	}

	@GetMapping("/{contatoId}")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Detalhar contato")
	public ContatoEmergenciaResponse buscar(@PathVariable Long perfilId, @PathVariable Long contatoId) {
		return perfis.buscarContato(perfilId, contatoId, usuario.idNumerico());
	}

	@PutMapping("/{contatoId}")
	@PreAuthorize("hasRole('CUIDADOR')")
	@Operation(summary = "Atualizar contato", description = "Só o cuidador responsável pelo perfil.")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "filha",
			value = """
					{"nome": "Ana Souza", "telefone": "(11) 91234-5678", "parentesco": "filha", "prioridade": 1}""")))
	public ContatoEmergenciaResponse atualizar(@PathVariable Long perfilId, @PathVariable Long contatoId,
			@Valid @RequestBody ContatoEmergenciaRequest request) {
		return perfis.atualizarContato(perfilId, contatoId, usuario.idNumerico(), request);
	}

	@DeleteMapping("/{contatoId}")
	@ApiResponse(responseCode = "204", description = "Contato excluído")
	@PreAuthorize("hasRole('CUIDADOR')")
	@Operation(summary = "Excluir contato", description = "Só o cuidador responsável pelo perfil.")
	public ResponseEntity<Void> excluir(@PathVariable Long perfilId, @PathVariable Long contatoId) {
		perfis.excluirContato(perfilId, contatoId, usuario.idNumerico());
		return ResponseEntity.noContent().build();
	}

}
