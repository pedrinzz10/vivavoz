package br.com.fiap.exemplosarquitetura.ruim;

import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Exemplo de violações do padrão DTO, usado só para provar que as regras de arquitetura funcionam.
 */
@RestController
class ControllerForaDoPadrao {

	@GetMapping("/mapa")
	Map<String, Object> devolveMapa() {
		return Map.of();
	}

	@GetMapping("/entidades")
	List<EntidadeExposta> devolveEntidades() {
		return List.of();
	}

	@PostMapping("/entidade")
	void recebeEntidade(@RequestBody EntidadeExposta entidade) {
	}

	@Entity
	static class EntidadeExposta {
	}

}
