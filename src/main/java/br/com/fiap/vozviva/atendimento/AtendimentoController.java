package br.com.fiap.vozviva.atendimento;

import br.com.fiap.vozviva.atendimento.dto.AtendimentosProximosResponse;
import br.com.fiap.vozviva.atendimento.dto.PontoAtendimentoResponse;
import br.com.fiap.vozviva.comum.dto.PaginaResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/atendimentos")
public class AtendimentoController {

	private final AtendimentoService atendimentos;

	public AtendimentoController(AtendimentoService atendimentos) {
		this.atendimentos = atendimentos;
	}

	/**
	 * "Hospital mais perto": locais do tipo pedido no raio, do mais próximo para o mais distante.
	 * A localização enviada não é gravada.
	 */
	@GetMapping("/proximos")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public AtendimentosProximosResponse proximos(
			@RequestParam TipoAtendimento tipo,
			@RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
			@RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
			@RequestParam(defaultValue = "false") boolean acessivel,
			@RequestParam(defaultValue = "5000") @Min(100) @Max(20000) int raioMetros,
			@RequestParam(defaultValue = "3") @Min(1) @Max(10) int limite) {
		return atendimentos.proximos(tipo, latitude, longitude, acessivel, raioMetros, limite);
	}

	/** Todos os locais (opcionalmente de um tipo). Ordenação: nome ou tipo. */
	@GetMapping
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public PaginaResponse<PontoAtendimentoResponse> listar(@RequestParam(required = false) TipoAtendimento tipo,
			@PageableDefault(size = 10, sort = "nome") Pageable pageable) {
		return atendimentos.listar(tipo, pageable);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public PontoAtendimentoResponse buscar(@PathVariable Long id) {
		return atendimentos.buscar(id);
	}

}
