package br.com.fiap.vozviva.comum.erro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeoutException;

/**
 * Controller usado apenas nos testes para disparar cada tipo de erro.
 */
@RestController
@RequestMapping("/teste-erros")
class ErroTesteController {

	@PostMapping("/validacao")
	String validacao(@Valid @RequestBody Pedido pedido) {
		return "ok";
	}

	@GetMapping("/parametro")
	String parametro(@RequestParam @Min(-90) @Max(90) double lat) {
		return "ok";
	}

	@GetMapping("/nao-encontrado")
	String naoEncontrado() {
		throw new RecursoNaoEncontradoException("Obstrução 123 não encontrada.");
	}

	@GetMapping("/conflito")
	String conflito() {
		throw new ConflitoException("Obstrução já registrada neste local.");
	}

	@GetMapping("/indisponivel")
	String indisponivel() {
		throw new ServicoIndisponivelException("API .NET não respondeu.");
	}

	@GetMapping("/timeout")
	String timeout() throws TimeoutException {
		throw new TimeoutException("leitura excedeu 3000ms em http://interno:5000/agendamentos");
	}

	@GetMapping("/inesperado")
	String inesperado() {
		throw new IllegalStateException("detalhe interno: senha=abc123 em br.com.fiap.vozviva.Segredo");
	}

	@GetMapping("/acesso-negado")
	String acessoNegado() {
		throw new AccessDeniedException("sem papel CUIDADOR");
	}

	record Pedido(@NotBlank String nome, @PositiveOrZero int idade) {
	}

}
