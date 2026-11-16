package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.dto.CadastroUsuarioRequest;
import br.com.fiap.vozviva.dto.LoginRequest;
import br.com.fiap.vozviva.dto.TokenResponse;
import br.com.fiap.vozviva.dto.UsuarioResponse;
import br.com.fiap.vozviva.exception.ConflitoException;
import br.com.fiap.vozviva.exception.ContaInexistenteException;
import br.com.fiap.vozviva.exception.CredenciaisInvalidasException;
import br.com.fiap.vozviva.exception.RecursoNaoEncontradoException;
import br.com.fiap.vozviva.mapper.UsuarioMapper;
import br.com.fiap.vozviva.model.Usuario;
import br.com.fiap.vozviva.repository.UsuarioRepository;
import br.com.fiap.vozviva.security.EmissorToken;
import br.com.fiap.vozviva.security.EmissorToken.TokenEmitido;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AutenticacaoService {

	private static final String TIPO_TOKEN = "Bearer";

	private final UsuarioRepository usuarios;
	private final PasswordEncoder senhas;
	private final EmissorToken emissor;
	private final LimitadorTentativasLogin limitador;
	private final PerfilEmergenciaService perfis;

	/**
	 * Hash usado quando o e-mail não existe: a verificação de senha roda do mesmo jeito, para que o
	 * tempo de resposta não revele se a conta existe.
	 */
	private final String hashFicticio;

	public AutenticacaoService(UsuarioRepository usuarios, PasswordEncoder senhas, EmissorToken emissor,
			LimitadorTentativasLogin limitador, PerfilEmergenciaService perfis) {
		this.usuarios = usuarios;
		this.senhas = senhas;
		this.emissor = emissor;
		this.limitador = limitador;
		this.perfis = perfis;
		// Valor aleatório a cada inicialização: não existe texto fixo que corresponda a esse hash.
		this.hashFicticio = senhas.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public UsuarioResponse cadastrar(CadastroUsuarioRequest request) {
		String email = normalizarEmail(request.email());
		if (usuarios.existsByEmail(email)) {
			throw new ConflitoException("Já existe uma conta com este e-mail.");
		}
		Usuario usuario = new Usuario(request.nome().trim(), email, senhas.encode(request.senha()), request.papel());
		// O DTO só deixa passar consentimento = true; a data do aceite é a prova para a LGPD.
		usuario.registrarConsentimento(Instant.now());
		try {
			return UsuarioMapper.paraResponse(usuarios.saveAndFlush(usuario));
		}
		catch (DataIntegrityViolationException ex) {
			// Dois cadastros simultâneos com o mesmo e-mail: a restrição única do banco decide.
			throw new ConflitoException("Já existe uma conta com este e-mail.");
		}
	}

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest request) {
		String email = normalizarEmail(request.email());
		// A tentativa é reservada antes de conferir a senha: chamadas simultâneas não furam o limite.
		limitador.consumirTentativa(email);

		Optional<Usuario> encontrado = usuarios.findByEmail(email);
		String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
		boolean senhaConfere = senhas.matches(request.senha(), hash);

		if (encontrado.isEmpty() || !senhaConfere || !encontrado.get().isAtivo()) {
			throw new CredenciaisInvalidasException();
		}
		limitador.registrarSucesso(email);

		Usuario usuario = encontrado.get();
		TokenEmitido token = emissor.emitir(usuario.getId().toString(), usuario.getNome(), usuario.getPapel());
		return new TokenResponse(token.valor(), TIPO_TOKEN, token.expiraEm().toString(), UsuarioMapper.paraResponse(usuario));
	}

	@Transactional(readOnly = true)
	public UsuarioResponse buscar(Long id) {
		return usuarios.findById(id)
			.map(UsuarioMapper::paraResponse)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
	}

	/**
	 * Exclusão da conta (LGPD): apaga o usuário do token e o que depende dele. O perfil do cidadão vai junto
	 * (com contatos, emergências e avisos); os perfis que um cuidador cuidava continuam com os cidadãos, sem o
	 * vínculo. O histórico de voz é apagado pelo banco (ON DELETE CASCADE). As estatísticas de golpe já são
	 * anônimas e não têm usuário.
	 */
	@Transactional
	public void excluirConta(Long id) {
		Usuario usuario = usuarios.findById(id).orElseThrow(ContaInexistenteException::new);
		perfis.removerDadosDoUsuario(id);
		usuarios.delete(usuario);
		usuarios.flush();
	}

	static String normalizarEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
