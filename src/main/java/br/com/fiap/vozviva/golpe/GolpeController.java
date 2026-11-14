package br.com.fiap.vozviva.golpe;

import br.com.fiap.vozviva.golpe.dto.EstatisticaGolpeResponse;
import br.com.fiap.vozviva.golpe.dto.VerificacaoRequest;
import br.com.fiap.vozviva.golpe.dto.VerificacaoResponse;
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
public class GolpeController {

	private final AssistenteGolpeService assistente;

	public GolpeController(AssistenteGolpeService assistente) {
		this.assistente = assistente;
	}

	/** "Isso é golpe?": analisa a mensagem recebida com IA + base de orientações oficiais + regras. */
	@PostMapping
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public VerificacaoResponse verificar(@Valid @RequestBody VerificacaoRequest request) {
		return assistente.verificar(request);
	}

	/** Golpes mais frequentes no período (dados anonimizados). */
	@GetMapping("/estatisticas")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public List<EstatisticaGolpeResponse> estatisticas(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias) {
		return assistente.estatisticas(dias);
	}

}
