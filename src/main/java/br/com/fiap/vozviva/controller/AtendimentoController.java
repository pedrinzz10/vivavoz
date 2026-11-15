package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.config.OpenApiConfig;
import br.com.fiap.vozviva.dto.AtendimentosProximosResponse;
import br.com.fiap.vozviva.dto.PaginaResponse;
import br.com.fiap.vozviva.dto.PontoAtendimentoResponse;
import br.com.fiap.vozviva.model.TipoAtendimento;
import br.com.fiap.vozviva.service.AtendimentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = OpenApiConfig.TAG_ATENDIMENTOS)
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
	@Operation(summary = "Atendimento mais perto de mim", description = "Locais do tipo pedido no raio, do mais próximo "
			+ "para o mais distante, com acessibilidade, telefone e links de rota (Google Maps, Waze, geo:). Com acessivel=true, "
			+ "só os de acessibilidade confirmada; sem nenhum, os de acessibilidade não informada, sinalizados. Hospital e "
			+ "UPA trazem a orientação do 192. A localização não é gravada. Dados © colaboradores do OpenStreetMap.")
	public AtendimentosProximosResponse proximos(
			@Parameter(example = "HOSPITAL") @RequestParam TipoAtendimento tipo,
			@Parameter(example = "-23.52648") @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
			@Parameter(example = "-46.77665") @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
			@Parameter(description = "Só locais com acessibilidade confirmada", example = "true")
			@RequestParam(defaultValue = "false") boolean acessivel,
			@Parameter(description = "Raio em metros (100 a 20000)", example = "5000") @RequestParam(defaultValue = "5000") @Min(100) @Max(20000) int raioMetros,
			@Parameter(description = "Quantos resultados (1 a 10)", example = "3") @RequestParam(defaultValue = "3") @Min(1) @Max(10) int limite) {
		return atendimentos.proximos(tipo, latitude, longitude, acessivel, raioMetros, limite);
	}

	/** Todos os locais (opcionalmente de um tipo). Ordenação: nome ou tipo. */
	@GetMapping
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Listar locais", description = "Paginado, opcionalmente de um tipo; ordenação por nome (padrão) ou tipo.")
	public PaginaResponse<PontoAtendimentoResponse> listar(
			@Parameter(example = "UBS") @RequestParam(required = false) TipoAtendimento tipo,
			@ParameterObject @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
		return atendimentos.listar(tipo, pageable);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Detalhar local", description = "Com os links de rota.")
	public PontoAtendimentoResponse buscar(@PathVariable Long id) {
		return atendimentos.buscar(id);
	}

}
