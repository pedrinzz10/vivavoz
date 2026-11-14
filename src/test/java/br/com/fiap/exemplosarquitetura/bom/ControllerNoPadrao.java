package br.com.fiap.exemplosarquitetura.bom;

import br.com.fiap.exemplosarquitetura.bom.dto.ObstrucaoRequest;
import br.com.fiap.exemplosarquitetura.bom.dto.ObstrucaoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exemplo de controller no padrão DTO, usado só nos testes de arquitetura.
 */
@RestController
class ControllerNoPadrao {

	@PostMapping("/obstrucoes")
	ResponseEntity<ObstrucaoResponse> criar(@RequestBody ObstrucaoRequest request) {
		return ResponseEntity.ok(new ObstrucaoResponse("1", request.tipo(), "P-1"));
	}

	@GetMapping("/obstrucoes")
	List<ObstrucaoResponse> listar() {
		return List.of();
	}

	@GetMapping("/obstrucoes/{id}")
	ObstrucaoResponse buscar(@PathVariable String id) {
		return new ObstrucaoResponse(id, "BURACO", "P-1");
	}

}
