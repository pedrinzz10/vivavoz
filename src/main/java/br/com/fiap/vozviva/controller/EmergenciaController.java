package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.config.OpenApiConfig;
import br.com.fiap.vozviva.dto.AcionarEmergenciaRequest;
import br.com.fiap.vozviva.dto.AvisoEmergenciaResponse;
import br.com.fiap.vozviva.dto.EmergenciaResponse;
import br.com.fiap.vozviva.dto.PaginaResponse;
import br.com.fiap.vozviva.security.UsuarioAutenticado;
import br.com.fiap.vozviva.service.EmergenciaService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/emergencias")
@Tag(name = OpenApiConfig.TAG_EMERGENCIA)
public class EmergenciaController {

	private final EmergenciaService emergencias;
	private final UsuarioAutenticado usuario;

	public EmergenciaController(EmergenciaService emergencias, UsuarioAutenticado usuario) {
		this.emergencias = emergencias;
		this.usuario = usuario;
	}

	/**
	 * Botão de emergência. Responde 202 na hora: o aviso aos contatos acontece em segundo plano,
	 * pelo RabbitMQ. Um toque repetido em até 2 minutos devolve a emergência que já está aberta.
	 */
	@PostMapping
	@PreAuthorize("hasRole('CIDADAO')")
	@Operation(summary = "Acionar o botão de emergência", description = "Responde 202 na hora; os contatos são avisados em "
			+ "segundo plano pelo RabbitMQ, em ordem de prioridade. Um segundo toque em até 2 minutos devolve a mesma "
			+ "emergência. A localização é opcional (latitude e longitude juntas).")
	@ApiResponse(responseCode = "202", description = "Emergência registrada; os contatos estão sendo avisados")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "queda",
			value = """
					{"latitude": -23.532900, "longitude": -46.791700, "textoOriginal": "caí e não consigo levantar"}""")))
	public ResponseEntity<EmergenciaResponse> acionar(@Valid @RequestBody AcionarEmergenciaRequest request) {
		EmergenciaService.Acionamento acionamento = emergencias.acionar(usuario.idNumerico(), request);
		URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
			.buildAndExpand(acionamento.emergencia().id()).toUri();
		return ResponseEntity.accepted().location(local).body(acionamento.emergencia());
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Listar emergências", description = "Cidadão: as próprias. Cuidador: as dos perfis que gerencia. "
			+ "Ordenação por acionadaEm (padrão, mais recentes primeiro), status ou id.")
	public PaginaResponse<EmergenciaResponse> listar(
			@ParameterObject @PageableDefault(size = 10, sort = "acionadaEm", direction = Sort.Direction.DESC) Pageable pageable) {
		return emergencias.listar(usuario.idNumerico(), usuario.papeis(), pageable);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Acompanhar emergência", description = "Status: ACIONADA, NOTIFICADA, FALHA_NO_AVISO (ligar 192), "
			+ "ATENDIDA ou CANCELADA, com a mensagemFalada correspondente.")
	public EmergenciaResponse buscar(@PathVariable Long id) {
		return emergencias.buscar(id, usuario.idNumerico());
	}

	/** Quem foi avisado e com qual mensagem. */
	@GetMapping("/{id}/avisos")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Avisos enviados", description = "Quem foi avisado, quando e com qual mensagem.")
	public List<AvisoEmergenciaResponse> avisos(@PathVariable Long id) {
		return emergencias.avisos(id, usuario.idNumerico());
	}

	@PatchMapping("/{id}/cancelar")
	@ApiResponse(responseCode = "200", description = "Emergência cancelada")
	@ApiResponse(responseCode = "409", description = "A emergência já foi encerrada")
	@PreAuthorize("hasRole('CIDADAO')")
	@Operation(summary = "Cancelar emergência", description = "Só o próprio cidadão, enquanto estiver aberta (409 se já encerrada).")
	public EmergenciaResponse cancelar(@PathVariable Long id) {
		return emergencias.cancelar(id, usuario.idNumerico());
	}

	@PatchMapping("/{id}/atender")
	@ApiResponse(responseCode = "200", description = "Emergência marcada como atendida")
	@ApiResponse(responseCode = "409", description = "A emergência já foi encerrada")
	@PreAuthorize("hasRole('CUIDADOR')")
	@Operation(summary = "Marcar como atendida", description = "Só o cuidador responsável, enquanto estiver aberta.")
	public EmergenciaResponse atender(@PathVariable Long id) {
		return emergencias.atender(id, usuario.idNumerico());
	}

}
