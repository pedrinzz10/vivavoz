package br.com.fiap.vozviva.emergencia;

import br.com.fiap.vozviva.comum.dto.PaginaResponse;
import br.com.fiap.vozviva.emergencia.dto.AcionarEmergenciaRequest;
import br.com.fiap.vozviva.emergencia.dto.AvisoEmergenciaResponse;
import br.com.fiap.vozviva.emergencia.dto.EmergenciaResponse;
import br.com.fiap.vozviva.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
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
	public ResponseEntity<EmergenciaResponse> acionar(@Valid @RequestBody AcionarEmergenciaRequest request) {
		EmergenciaService.Acionamento acionamento = emergencias.acionar(usuario.idNumerico(), request);
		URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
			.buildAndExpand(acionamento.emergencia().id()).toUri();
		return ResponseEntity.accepted().location(local).body(acionamento.emergencia());
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public PaginaResponse<EmergenciaResponse> listar(
			@PageableDefault(size = 10, sort = "acionadaEm", direction = Sort.Direction.DESC) Pageable pageable) {
		return emergencias.listar(usuario.idNumerico(), usuario.papeis(), pageable);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public EmergenciaResponse buscar(@PathVariable Long id) {
		return emergencias.buscar(id, usuario.idNumerico());
	}

	/** Quem foi avisado e com qual mensagem. */
	@GetMapping("/{id}/avisos")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public List<AvisoEmergenciaResponse> avisos(@PathVariable Long id) {
		return emergencias.avisos(id, usuario.idNumerico());
	}

	@PatchMapping("/{id}/cancelar")
	@PreAuthorize("hasRole('CIDADAO')")
	public EmergenciaResponse cancelar(@PathVariable Long id) {
		return emergencias.cancelar(id, usuario.idNumerico());
	}

	@PatchMapping("/{id}/atender")
	@PreAuthorize("hasRole('CUIDADOR')")
	public EmergenciaResponse atender(@PathVariable Long id) {
		return emergencias.atender(id, usuario.idNumerico());
	}

}
