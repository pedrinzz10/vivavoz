package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.config.OpenApiConfig;
import br.com.fiap.vozviva.dto.EstatisticaGolpeResponse;
import br.com.fiap.vozviva.dto.VerificacaoRequest;
import br.com.fiap.vozviva.dto.VerificacaoResponse;
import br.com.fiap.vozviva.service.AssistenteGolpeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/verificacoes")
@Tag(name = OpenApiConfig.TAG_GOLPES)
public class GolpeController {

	private final AssistenteGolpeService assistente;

	public GolpeController(AssistenteGolpeService assistente) {
		this.assistente = assistente;
	}

	/** "Isso é golpe?": analisa a mensagem recebida com IA + base de orientações oficiais + regras. */
	@PostMapping
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Isso é golpe?", description = "Classifica a mensagem recebida (PROVAVEL_GOLPE, SUSPEITA, "
			+ "SEM_SINAIS_DE_RISCO, NAO_E_POSSIVEL_AVALIAR) com IA (OCI Generative AI) + orientações oficiais (RAG) + regras. "
			+ "Traz os sinais, o que fazer, o canal oficial e as fontes. O texto da mensagem não é gravado.")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "falso INSS",
			value = """
					{"texto": "INSS: seu benefício será bloqueado hoje. Atualize seus dados em http://inss-atualiza.com", "canal": "SMS"}""")))
	public VerificacaoResponse verificar(@Valid @RequestBody VerificacaoRequest request) {
		return assistente.verificar(request);
	}

	/** Golpes mais frequentes no período (dados anonimizados). */
	@GetMapping("/estatisticas")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Golpes mais frequentes", description = "Contagem anonimizada por tipo de golpe no período.")
	public List<EstatisticaGolpeResponse> estatisticas(
			@Parameter(description = "Período em dias (1 a 365)", example = "30") @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias) {
		return assistente.estatisticas(dias);
	}

}
