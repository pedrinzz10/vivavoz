package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.dto.AtualizarPerfilEmergenciaRequest;
import br.com.fiap.vozviva.dto.ContatoEmergenciaRequest;
import br.com.fiap.vozviva.dto.ContatoEmergenciaResponse;
import br.com.fiap.vozviva.dto.PaginaResponse;
import br.com.fiap.vozviva.dto.PerfilEmergenciaRequest;
import br.com.fiap.vozviva.dto.PerfilEmergenciaResponse;
import br.com.fiap.vozviva.exception.ConflitoException;
import br.com.fiap.vozviva.exception.ContaInexistenteException;
import br.com.fiap.vozviva.exception.RecursoNaoEncontradoException;
import br.com.fiap.vozviva.exception.RequisicaoInvalidaException;
import br.com.fiap.vozviva.mapper.PerfilEmergenciaMapper;
import br.com.fiap.vozviva.model.ContatoEmergencia;
import br.com.fiap.vozviva.model.Papel;
import br.com.fiap.vozviva.model.PerfilEmergencia;
import br.com.fiap.vozviva.model.Usuario;
import br.com.fiap.vozviva.repository.ContatoEmergenciaRepository;
import br.com.fiap.vozviva.repository.PerfilEmergenciaRepository;
import br.com.fiap.vozviva.repository.UsuarioRepository;
import br.com.fiap.vozviva.util.Ordenacao;
import br.com.fiap.vozviva.util.Telefones;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * Regras do perfil de emergência e dos contatos:
 * <ul>
 *   <li>o cidadão cria e mantém o próprio perfil e os contatos (quem mora sozinho cadastra quem avisar);</li>
 *   <li>o cuidador cria o perfil de um cidadão pelo e-mail dele e também pode alterá-lo, excluí-lo e mexer
 *   nos contatos; um perfil pode não ter cuidador;</li>
 *   <li>cada cidadão tem no máximo um perfil, com até {@value #MAXIMO_CONTATOS} contatos.</li>
 * </ul>
 */
@Service
public class PerfilEmergenciaService {

	public static final int MAXIMO_CONTATOS = 10;

	private static final Set<String> ORDENACAO_PERFIS = Set.of("id", "mobilidade", "criadoEm", "atualizadoEm");
	private static final Set<String> ORDENACAO_CONTATOS = Set.of("id", "nome", "prioridade", "parentesco");

	private final PerfilEmergenciaRepository perfis;
	private final ContatoEmergenciaRepository contatos;
	private final UsuarioRepository usuarios;

	public PerfilEmergenciaService(PerfilEmergenciaRepository perfis, ContatoEmergenciaRepository contatos,
			UsuarioRepository usuarios) {
		this.perfis = perfis;
		this.contatos = contatos;
		this.usuarios = usuarios;
	}

	// --- Perfil -------------------------------------------------------------

	/**
	 * O cidadão cria o próprio perfil (vale o dono do token; quem mora sozinho cadastra quem avisar). O cuidador
	 * cria o perfil de um cidadão pelo e-mail da conta dele e fica responsável pelo perfil.
	 */
	@Transactional
	public PerfilEmergenciaResponse criar(Long usuarioId, Papel papel, PerfilEmergenciaRequest request) {
		Usuario autor = usuarios.findById(usuarioId).orElseThrow(ContaInexistenteException::new);
		String email = StringUtils.hasText(request.cidadaoEmail())
				? request.cidadaoEmail().trim().toLowerCase(Locale.ROOT) : null;
		Usuario cidadao;
		Usuario cuidador;
		if (papel == Papel.CIDADAO) {
			if (email != null && !email.equals(autor.getEmail())) {
				throw new AccessDeniedException("O cidadão só cria o próprio perfil.");
			}
			cidadao = autor;
			cuidador = null;
		}
		else {
			if (email == null) {
				throw new RequisicaoInvalidaException("Informe o e-mail da conta do cidadão.");
			}
			cidadao = usuarios.findByEmail(email)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Não existe conta com o e-mail informado."));
			if (cidadao.getPapel() != Papel.CIDADAO) {
				throw new RequisicaoInvalidaException("O e-mail informado não é de uma conta de cidadão.");
			}
			cuidador = autor;
		}
		if (perfis.existsByCidadaoId(cidadao.getId())) {
			throw new ConflitoException("Este cidadão já tem um perfil de emergência.");
		}
		PerfilEmergencia perfil = new PerfilEmergencia(cidadao, cuidador);
		perfil.atualizar(request.mobilidade(), request.condicoesSaude(), request.medicamentos(), request.observacoes());
		PerfilEmergencia salvo;
		try {
			salvo = perfis.saveAndFlush(perfil);
		}
		catch (DataIntegrityViolationException ex) {
			// Dois cuidadores criando o perfil do mesmo cidadão ao mesmo tempo: a restrição única decide.
			throw new ConflitoException("Este cidadão já tem um perfil de emergência.");
		}
		return PerfilEmergenciaMapper.paraResponse(carregar(salvo.getId()));
	}

	@Transactional(readOnly = true)
	public PerfilEmergenciaResponse buscar(Long perfilId, Long usuarioId) {
		return PerfilEmergenciaMapper.paraResponse(perfilVisivelPara(perfilId, usuarioId));
	}

	@Transactional(readOnly = true)
	public PerfilEmergenciaResponse buscarDoCidadao(Long cidadaoId) {
		return perfis.findByCidadaoId(cidadaoId)
			.map(PerfilEmergenciaMapper::paraResponse)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Você ainda não tem perfil de emergência."));
	}

	@Transactional(readOnly = true)
	public PaginaResponse<PerfilEmergenciaResponse> listarDoCuidador(Long cuidadorId, Pageable pageable) {
		Ordenacao.validar(pageable, ORDENACAO_PERFIS);
		return PaginaResponse.de(perfis.findByCuidadorId(cuidadorId, pageable), PerfilEmergenciaMapper::paraResponse);
	}

	@Transactional
	public PerfilEmergenciaResponse atualizar(Long perfilId, Long usuarioId, AtualizarPerfilEmergenciaRequest request) {
		PerfilEmergencia perfil = perfilAlteravelPor(perfilId, usuarioId);
		perfil.atualizar(request.mobilidade(), request.condicoesSaude(), request.medicamentos(), request.observacoes());
		perfis.flush();
		return PerfilEmergenciaMapper.paraResponse(perfil);
	}

	@Transactional
	public void excluir(Long perfilId, Long usuarioId) {
		perfis.delete(perfilAlteravelPor(perfilId, usuarioId));
	}

	/**
	 * Exclusão da conta (LGPD): apaga o perfil do próprio cidadão (com contatos, emergências e avisos) e tira o
	 * vínculo do cuidador com os perfis que ele cuidava. Esses perfis são de outras pessoas e continuam com elas.
	 */
	@Transactional
	public void removerDadosDoUsuario(Long usuarioId) {
		perfis.findByCidadaoId(usuarioId).ifPresent(perfis::delete);
		perfis.desvincularCuidador(usuarioId);
	}

	// --- Contatos -----------------------------------------------------------

	@Transactional
	public ContatoEmergenciaResponse adicionarContato(Long perfilId, Long usuarioId, ContatoEmergenciaRequest request) {
		// Trava o perfil: inclusões simultâneas não conseguem passar do limite de contatos.
		PerfilEmergencia perfil = perfis.findParaAlterarById(perfilId)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de emergência não encontrado."));
		if (!perfil.podeSerAlteradoPor(usuarioId)) {
			throw new AccessDeniedException("Só o cidadão dono do perfil ou o cuidador responsável alteram os contatos.");
		}
		if (contatos.countByPerfilId(perfilId) >= MAXIMO_CONTATOS) {
			throw new ConflitoException("O perfil já tem o máximo de " + MAXIMO_CONTATOS + " contatos.");
		}
		ContatoEmergencia contato = new ContatoEmergencia(perfil);
		preencher(contato, request);
		return PerfilEmergenciaMapper.paraResponse(contatos.saveAndFlush(contato));
	}

	@Transactional(readOnly = true)
	public PaginaResponse<ContatoEmergenciaResponse> listarContatos(Long perfilId, Long usuarioId, Pageable pageable) {
		Ordenacao.validar(pageable, ORDENACAO_CONTATOS);
		perfilVisivelPara(perfilId, usuarioId);
		return PaginaResponse.de(contatos.findByPerfilId(perfilId, pageable), PerfilEmergenciaMapper::paraResponse);
	}

	@Transactional(readOnly = true)
	public ContatoEmergenciaResponse buscarContato(Long perfilId, Long contatoId, Long usuarioId) {
		perfilVisivelPara(perfilId, usuarioId);
		return PerfilEmergenciaMapper.paraResponse(contatoDoPerfil(perfilId, contatoId));
	}

	@Transactional
	public ContatoEmergenciaResponse atualizarContato(Long perfilId, Long contatoId, Long usuarioId,
			ContatoEmergenciaRequest request) {
		perfilAlteravelPor(perfilId, usuarioId);
		ContatoEmergencia contato = contatoDoPerfil(perfilId, contatoId);
		preencher(contato, request);
		contatos.flush();
		return PerfilEmergenciaMapper.paraResponse(contato);
	}

	@Transactional
	public void excluirContato(Long perfilId, Long contatoId, Long usuarioId) {
		perfilAlteravelPor(perfilId, usuarioId);
		contatos.delete(contatoDoPerfil(perfilId, contatoId));
	}

	// --- Regras de acesso ---------------------------------------------------

	private PerfilEmergencia carregar(Long perfilId) {
		return perfis.findWithUsuariosById(perfilId)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de emergência não encontrado."));
	}

	/** O cuidador responsável e o próprio cidadão podem ver o perfil. */
	private PerfilEmergencia perfilVisivelPara(Long perfilId, Long usuarioId) {
		PerfilEmergencia perfil = carregar(perfilId);
		if (!perfil.ehGerenciadoPor(usuarioId) && !perfil.pertenceAo(usuarioId)) {
			throw new AccessDeniedException("Perfil de outro cidadão.");
		}
		return perfil;
	}

	/** O cuidador responsável e o cidadão dono do perfil alteram o perfil e os contatos. */
	private PerfilEmergencia perfilAlteravelPor(Long perfilId, Long usuarioId) {
		PerfilEmergencia perfil = carregar(perfilId);
		if (!perfil.podeSerAlteradoPor(usuarioId)) {
			throw new AccessDeniedException("Só o cidadão dono do perfil ou o cuidador responsável podem alterá-lo.");
		}
		return perfil;
	}

	private ContatoEmergencia contatoDoPerfil(Long perfilId, Long contatoId) {
		return contatos.findByIdAndPerfilId(contatoId, perfilId)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Contato não encontrado neste perfil."));
	}

	private static void preencher(ContatoEmergencia contato, ContatoEmergenciaRequest request) {
		contato.atualizar(request.nome().trim(), Telefones.normalizar(request.telefone()),
				request.parentesco() == null ? null : request.parentesco().trim(), request.prioridade());
	}

}
