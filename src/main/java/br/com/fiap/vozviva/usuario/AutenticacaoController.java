package br.com.fiap.vozviva.usuario;

import br.com.fiap.vozviva.seguranca.UsuarioAutenticado;
import br.com.fiap.vozviva.usuario.dto.CadastroUsuarioRequest;
import br.com.fiap.vozviva.usuario.dto.LoginRequest;
import br.com.fiap.vozviva.usuario.dto.TokenResponse;
import br.com.fiap.vozviva.usuario.dto.UsuarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacaoController {

	private final AutenticacaoService autenticacao;
	private final UsuarioAutenticado usuarioAutenticado;

	public AutenticacaoController(AutenticacaoService autenticacao, UsuarioAutenticado usuarioAutenticado) {
		this.autenticacao = autenticacao;
		this.usuarioAutenticado = usuarioAutenticado;
	}

	@PostMapping("/cadastro")
	public ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody CadastroUsuarioRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(autenticacao.cadastrar(request));
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return autenticacao.login(request);
	}

	@GetMapping("/eu")
	public UsuarioResponse eu() {
		return autenticacao.buscar(usuarioAutenticado.idNumerico());
	}

}
