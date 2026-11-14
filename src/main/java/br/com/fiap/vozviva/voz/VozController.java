package br.com.fiap.vozviva.voz;

import br.com.fiap.vozviva.comum.dto.PaginaResponse;
import br.com.fiap.vozviva.seguranca.UsuarioAutenticado;
import br.com.fiap.vozviva.voz.dto.IntencaoRequest;
import br.com.fiap.vozviva.voz.dto.IntencaoResponse;
import br.com.fiap.vozviva.voz.dto.InteracaoVozResponse;
import jakarta.validation.Valid;
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
	public IntencaoResponse classificar(@Valid @RequestBody IntencaoRequest request) {
		return voz.classificar(usuario.idNumerico(), request);
	}

	/** Histórico do próprio usuário. Ordenação padrão: mais recentes primeiro. */
	@GetMapping("/interacoes")
	@PreAuthorize("hasAnyRole('CIDADAO', 'CUIDADOR')")
	public PaginaResponse<InteracaoVozResponse> historico(
			@PageableDefault(size = 10, sort = "criadaEm", direction = Sort.Direction.DESC) Pageable pageable) {
		return voz.historico(usuario.idNumerico(), pageable);
	}

}
