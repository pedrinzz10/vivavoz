package br.com.fiap.vozviva.perfil;

import br.com.fiap.vozviva.comum.Ordenacao;
import br.com.fiap.vozviva.comum.dto.PaginaResponse;
import br.com.fiap.vozviva.comum.erro.ConflitoException;
import br.com.fiap.vozviva.comum.erro.RecursoNaoEncontradoException;
import br.com.fiap.vozviva.comum.erro.RequisicaoInvalidaException;
import br.com.fiap.vozviva.perfil.dto.AtualizarPerfilEmergenciaRequest;
import br.com.fiap.vozviva.perfil.dto.ContatoEmergenciaRequest;
import br.com.fiap.vozviva.perfil.dto.ContatoEmergenciaResponse;
import br.com.fiap.vozviva.perfil.dto.PerfilEmergenciaRequest;
import br.com.fiap.vozviva.perfil.dto.PerfilEmergenciaResponse;
import br.com.fiap.vozviva.seguranca.Papel;
import br.com.fiap.vozviva.usuario.Usuario;
import br.com.fiap.vozviva.usuario.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

/**
 * Regras do perfil de emergência e dos contatos:
 * <ul>
 *   <li>só o cuidador que criou o perfil pode alterá-lo, excluí-lo e mexer nos contatos;</li>
 *   <li>o cidadão dono do perfil pode consultá-lo (e os contatos), mas não alterar;</li>
 *   <li>cada cidadão tem no máximo um perfil, com até {@value #MAXIMO_CONTATOS} contatos.</li>
 * </ul>
 */
@Service
public class PerfilEmergenciaService {

	static final int MAXIMO_CONTATOS = 10;

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

	@Transactional
	public PerfilEmergenciaResponse criar(Long cuidadorId, PerfilEmergenciaRequest request) {
		String email = request.cidadaoEmail().trim().toLowerCase(Locale.ROOT);
		Usuario cidadao = usuarios.findByEmail(email)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Não existe conta com o e-mail informado."));
		if (cidadao.getPapel() != Papel.CIDADAO) {
			throw new RequisicaoInvalidaException("O e-mail informado não é de uma conta de cidadão.");
		}
		if (perfis.existsByCidadaoId(cidadao.getId())) {
			throw new ConflitoException("Este cidadão já tem um perfil de emergência.");
		}
		Usuario cuidador = usuarios.getReferenceById(cuidadorId);
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
	public PerfilEmergenciaResponse atualizar(Long perfilId, Long cuidadorId, AtualizarPerfilEmergenciaRequest request) {
		PerfilEmergencia perfil = perfilGerenciadoPor(perfilId, cuidadorId);
		perfil.atualizar(request.mobilidade(), request.condicoesSaude(), request.medicamentos(), request.observacoes());
		perfis.flush();
		return PerfilEmergenciaMapper.paraResponse(perfil);
	}

	@Transactional
	public void excluir(Long perfilId, Long cuidadorId) {
		perfis.delete(perfilGerenciadoPor(perfilId, cuidadorId));
	}

	// --- Contatos -----------------------------------------------------------

	@Transactional
	public ContatoEmergenciaResponse adicionarContato(Long perfilId, Long cuidadorId, ContatoEmergenciaRequest request) {
		// Trava o perfil: inclusões simultâneas não conseguem passar do limite de contatos.
		PerfilEmergencia perfil = perfis.findParaAlterarById(perfilId)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de emergência não encontrado."));
		if (!perfil.ehGerenciadoPor(cuidadorId)) {
			throw new AccessDeniedException("Perfil gerenciado por outro cuidador.");
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
	public ContatoEmergenciaResponse atualizarContato(Long perfilId, Long contatoId, Long cuidadorId,
			ContatoEmergenciaRequest request) {
		perfilGerenciadoPor(perfilId, cuidadorId);
		ContatoEmergencia contato = contatoDoPerfil(perfilId, contatoId);
		preencher(contato, request);
		contatos.flush();
		return PerfilEmergenciaMapper.paraResponse(contato);
	}

	@Transactional
	public void excluirContato(Long perfilId, Long contatoId, Long cuidadorId) {
		perfilGerenciadoPor(perfilId, cuidadorId);
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

	/** Só o cuidador responsável altera o perfil e os contatos. */
	private PerfilEmergencia perfilGerenciadoPor(Long perfilId, Long cuidadorId) {
		PerfilEmergencia perfil = carregar(perfilId);
		if (!perfil.ehGerenciadoPor(cuidadorId)) {
			throw new AccessDeniedException("Perfil gerenciado por outro cuidador.");
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
