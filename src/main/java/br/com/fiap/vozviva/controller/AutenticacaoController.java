package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.config.OpenApiConfig;
import br.com.fiap.vozviva.dto.AtualizarContaRequest;
import br.com.fiap.vozviva.dto.CadastroUsuarioRequest;
import br.com.fiap.vozviva.dto.LoginRequest;
import br.com.fiap.vozviva.dto.TokenResponse;
import br.com.fiap.vozviva.dto.TrocarSenhaRequest;
import br.com.fiap.vozviva.dto.UsuarioResponse;
import br.com.fiap.vozviva.security.UsuarioAutenticado;
import br.com.fiap.vozviva.service.AutenticacaoService;
import br.com.fiap.vozviva.service.ZeladoriaCliente;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = OpenApiConfig.TAG_AUTENTICACAO)
public class AutenticacaoController {

	private final AutenticacaoService autenticacao;
	private final UsuarioAutenticado usuarioAutenticado;
	private final ZeladoriaCliente zeladoria;

	public AutenticacaoController(AutenticacaoService autenticacao, UsuarioAutenticado usuarioAutenticado,
			ZeladoriaCliente zeladoria) {
		this.autenticacao = autenticacao;
		this.usuarioAutenticado = usuarioAutenticado;
		this.zeladoria = zeladoria;
	}

	@PostMapping("/cadastro")
	@ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
	@ApiResponse(responseCode = "201", description = "Conta criada")
	@SecurityRequirements
	@Operation(summary = "Cadastrar usuário", description = "Cria a conta de um cidadão (idoso ou PcD) ou de um cuidador. "
			+ "A senha precisa de 8 caracteres ou mais, com pelo menos uma letra e um número. O consentimento com os termos "
			+ "de uso e privacidade é obrigatório (consentimento = true) e fica gravado com a data do aceite (LGPD).")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "cidadão",
			value = """
					{"nome": "Maria da Silva", "email": "maria@exemplo.com", "senha": "girassol2026", "papel": "CIDADAO",
					 "consentimento": true}""")))
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

	@DeleteMapping("/eu")
	@ApiResponse(responseCode = "204", description = "Conta e dados apagados")
	@Operation(summary = "Excluir minha conta (LGPD)", description = "Apaga a conta do token e o que depende dela: o perfil "
			+ "de emergência do cidadão, com contatos, emergências e avisos, e o histórico de voz. Os perfis que um cuidador "
			+ "cuidava continuam com os cidadãos, sem o vínculo. As estatísticas de golpe já são anônimas. A API .NET de "
			+ "zeladoria também é avisada: apaga os reportes da pessoa e passa a recusar o token.")
	public ResponseEntity<Void> excluirConta(@AuthenticationPrincipal Jwt jwt) {
		// Primeiro a zeladoria, enquanto o token ainda vale para ela; depois a conta aqui.
		zeladoria.excluirConta(jwt == null ? null : jwt.getTokenValue());
		autenticacao.excluirConta(usuarioAutenticado.idNumerico());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/eu")
	@Operation(summary = "Quem sou eu", description = "Dados do usuário dono do token.")
	public UsuarioResponse eu() {
		return autenticacao.buscar(usuarioAutenticado.idNumerico());
	}

	@PutMapping("/eu")
	@ApiResponse(responseCode = "200", description = "Dados atualizados")
	@Operation(summary = "Corrigir meus dados (LGPD)", description = "Corrige o nome da conta do token (direito de "
			+ "correção da LGPD). O papel não muda por aqui, e o e-mail também não: sem confirmar o endereço novo, quem "
			+ "tivesse o token conseguiria tomar a conta. O nome novo vai para o token no próximo login.")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "nome",
			value = """
					{"nome": "Maria da Silva Souza"}""")))
	public UsuarioResponse atualizarConta(@Valid @RequestBody AtualizarContaRequest request) {
		return autenticacao.atualizarConta(usuarioAutenticado.idNumerico(), request);
	}

	@PutMapping("/eu/senha")
	@ApiResponse(responseCode = "204", description = "Senha trocada")
	@ApiResponse(responseCode = "429", description = "Muitas tentativas com a senha atual errada")
	@Operation(summary = "Trocar minha senha", description = "Exige a senha atual. A nova segue as regras do cadastro "
			+ "(8 caracteres ou mais, com letra e número) e precisa ser diferente da atual. Erros de senha atual contam no "
			+ "mesmo limite do login: 5 em 15 minutos bloqueiam (429).")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "troca",
			value = """
					{"senhaAtual": "girassol2026", "novaSenha": "ipe-amarelo-2027"}""")))
	public ResponseEntity<Void> trocarSenha(@Valid @RequestBody TrocarSenhaRequest request) {
		autenticacao.trocarSenha(usuarioAutenticado.idNumerico(), request);
		return ResponseEntity.noContent().build();
	}

}
