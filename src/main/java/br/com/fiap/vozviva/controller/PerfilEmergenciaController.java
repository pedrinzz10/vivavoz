package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.config.OpenApiConfig;
import br.com.fiap.vozviva.dto.AtualizarPerfilEmergenciaRequest;
import br.com.fiap.vozviva.dto.PaginaResponse;
import br.com.fiap.vozviva.dto.PerfilEmergenciaRequest;
import br.com.fiap.vozviva.dto.PerfilEmergenciaResponse;
import br.com.fiap.vozviva.model.Papel;
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
@Tag(name = OpenApiConfig.TAG_PERFIL)
public class PerfilEmergenciaController {

	private final PerfilEmergenciaService perfis;
	private final UsuarioAutenticado usuario;

	public PerfilEmergenciaController(PerfilEmergenciaService perfis, UsuarioAutenticado usuario) {
		this.perfis = perfis;
		this.usuario = usuario;
	}

	@PostMapping
	@ApiResponse(responseCode = "409", description = "O cidadão já tem perfil de emergência")
	@ApiResponse(responseCode = "201", description = "Perfil criado (header Location aponta para ele)")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Criar perfil de emergência", description = "O cidadão cria o próprio perfil (sem cidadaoEmail: vale o "
			+ "dono do token). O cuidador cria o perfil de um cidadão pelo e-mail da conta dele. A mobilidade é opcional "
			+ "(dado de saúde): sem ela, fica NAO_INFORMADO. Os dados vão na mensagem de socorro. Um perfil por cidadão "
			+ "(409 se já existir).")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
			@ExampleObject(name = "cidadão cria o próprio", value = """
					{"condicoesSaude": "Hipertensão", "medicamentos": "Losartana 50 mg"}"""),
			@ExampleObject(name = "cuidador cria para o cidadão", value = """
					{"cidadaoEmail": "maria@exemplo.com", "mobilidade": "CADEIRANTE", "condicoesSaude": "Hipertensão",
					 "medicamentos": "Losartana 50 mg", "observacoes": "Mora no térreo, portão azul"}""") }))
	public ResponseEntity<PerfilEmergenciaResponse> criar(@Valid @RequestBody PerfilEmergenciaRequest request) {
		Papel papel = usuario.possui(Papel.CUIDADOR) ? Papel.CUIDADOR : Papel.CIDADAO;
		PerfilEmergenciaResponse criado = perfis.criar(usuario.idNumerico(), papel, request);
		URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
		return ResponseEntity.created(local).body(criado);
	}

	/** Perfis que o cuidador gerencia. Ordenação padrão: mais recentes primeiro. */
	@GetMapping
	@PreAuthorize("hasRole('CUIDADOR')")
	@Operation(summary = "Perfis do cuidador", description = "Paginado; ordenação por criadoEm (padrão, mais recentes primeiro) ou id.")
	public PaginaResponse<PerfilEmergenciaResponse> listar(
			@ParameterObject @PageableDefault(size = 10, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
		return perfis.listarDoCuidador(usuario.idNumerico(), pageable);
	}

	@GetMapping("/meu")
	@PreAuthorize("hasRole('CIDADAO')")
	@Operation(summary = "Meu perfil", description = "O cidadão consulta o próprio perfil de emergência.")
	public PerfilEmergenciaResponse meu() {
		return perfis.buscarDoCidadao(usuario.idNumerico());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Detalhar perfil", description = "Visível para o próprio cidadão e para o cuidador responsável.")
	public PerfilEmergenciaResponse buscar(@PathVariable Long id) {
		return perfis.buscar(id, usuario.idNumerico());
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Atualizar perfil", description = "O cidadão dono do perfil ou o cuidador responsável. Mobilidade "
			+ "opcional (sem ela, NAO_INFORMADO).")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "andador",
			value = """
					{"mobilidade": "ANDADOR", "condicoesSaude": "Hipertensão e diabetes", "medicamentos": "Losartana, metformina",
					 "observacoes": "Usa andador desde 2025"}""")))
	public PerfilEmergenciaResponse atualizar(@PathVariable Long id,
			@Valid @RequestBody AtualizarPerfilEmergenciaRequest request) {
		return perfis.atualizar(id, usuario.idNumerico(), request);
	}

	@DeleteMapping("/{id}")
	@ApiResponse(responseCode = "204", description = "Perfil excluído")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Excluir perfil", description = "Remove o perfil e os contatos. O cidadão dono do perfil ou o "
			+ "cuidador responsável.")
	public ResponseEntity<Void> excluir(@PathVariable Long id) {
		perfis.excluir(id, usuario.idNumerico());
		return ResponseEntity.noContent().build();
	}

}
