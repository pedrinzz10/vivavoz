package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.config.OpenApiConfig;
import br.com.fiap.vozviva.dto.CadastroUsuarioRequest;
import br.com.fiap.vozviva.dto.LoginRequest;
import br.com.fiap.vozviva.dto.TokenResponse;
import br.com.fiap.vozviva.dto.UsuarioResponse;
import br.com.fiap.vozviva.security.UsuarioAutenticado;
import br.com.fiap.vozviva.service.AutenticacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = OpenApiConfig.TAG_AUTENTICACAO)
public class AutenticacaoController {

	private final AutenticacaoService autenticacao;
	private final UsuarioAutenticado usuarioAutenticado;

	public AutenticacaoController(AutenticacaoService autenticacao, UsuarioAutenticado usuarioAutenticado) {
		this.autenticacao = autenticacao;
		this.usuarioAutenticado = usuarioAutenticado;
	}

	@PostMapping("/cadastro")
	@ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
	@ApiResponse(responseCode = "201", description = "Conta criada")
	@SecurityRequirements
	@Operation(summary = "Cadastrar usuário", description = "Cria a conta de um cidadão (idoso ou PcD) ou de um cuidador. "
			+ "A senha precisa de 8 caracteres ou mais, com pelo menos uma letra e um número.")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "cidadão",
			value = """
					{"nome": "Maria da Silva", "email": "maria@exemplo.com", "senha": "girassol2026", "papel": "CIDADAO"}""")))
	public ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody CadastroUsuarioRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(autenticacao.cadastrar(request));
	}

	@PostMapping("/login")
	@SecurityRequirements
	@Operation(summary = "Entrar e receber o token", description = "Devolve o JWT para usar no botão Authorize e no header "
			+ "Authorization: Bearer. Depois de 5 tentativas erradas em 15 minutos, o e-mail fica bloqueado por um tempo (429).")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
			name = "usuário de teste (perfil dev)", value = """
					{"email": "cidadao.teste@vozviva.dev", "senha": "a senha definida em USUARIOS_TESTE_SENHA"}""")))
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return autenticacao.login(request);
	}

	@GetMapping("/eu")
	@Operation(summary = "Quem sou eu", description = "Dados do usuário dono do token.")
	public UsuarioResponse eu() {
		return autenticacao.buscar(usuarioAutenticado.idNumerico());
	}

}
