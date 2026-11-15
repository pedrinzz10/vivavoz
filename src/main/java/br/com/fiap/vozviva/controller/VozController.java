package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.config.OpenApiConfig;
import br.com.fiap.vozviva.dto.IntencaoRequest;
import br.com.fiap.vozviva.dto.IntencaoResponse;
import br.com.fiap.vozviva.dto.InteracaoVozResponse;
import br.com.fiap.vozviva.dto.PaginaResponse;
import br.com.fiap.vozviva.security.UsuarioAutenticado;
import br.com.fiap.vozviva.service.IntencaoVozService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/voz")
@Tag(name = OpenApiConfig.TAG_VOZ)
public class VozController {

	private final IntencaoVozService voz;
	private final UsuarioAutenticado usuario;

	public VozController(IntencaoVozService voz, UsuarioAutenticado usuario) {
		this.voz = voz;
		this.usuario = usuario;
	}

	/** Recebe a fala transcrita e devolve qual tela o app deve abrir. Não executa nenhuma ação. */
	@PostMapping("/intencoes")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Classificar a fala", description = "Recebe a fala transcrita e devolve a intenção e a tela que o "
			+ "app deve abrir (ACIONAR_SOCORRO, MOSTRAR_ATENDIMENTOS, MOSTRAR_VERIFICACAO, ABRIR_ZELADORIA_DOTNET ou "
			+ "PEDIR_REPETICAO), com urgência, entidades e frase falada. Não executa nenhuma ação. Em busca de "
			+ "atendimento com localização, já traz os locais mais próximos.")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
			@ExampleObject(name = "buscar atendimento", value = """
					{"texto": "me leva no hospital mais perto, eu uso cadeira de rodas",
					 "localizacao": {"latitude": -23.52648, "longitude": -46.77665}}"""),
			@ExampleObject(name = "socorro", value = """
					{"texto": "caí e não consigo levantar"}"""),
			@ExampleObject(name = "problema na rua", value = """
					{"texto": "tem um buraco gigante na minha calçada e não passo com a cadeira"}""") }))
	public IntencaoResponse classificar(@Valid @RequestBody IntencaoRequest request) {
		return voz.classificar(usuario.idNumerico(), request);
	}

	/** Histórico do próprio usuário. Ordenação padrão: mais recentes primeiro. */
	@GetMapping("/interacoes")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	@Operation(summary = "Meu histórico de voz", description = "Paginado; ordenação por criadaEm (padrão, mais recentes primeiro) ou id.")
	public PaginaResponse<InteracaoVozResponse> historico(
			@ParameterObject @PageableDefault(size = 10, sort = "criadaEm", direction = Sort.Direction.DESC) Pageable pageable) {
		return voz.historico(usuario.idNumerico(), pageable);
	}

}
