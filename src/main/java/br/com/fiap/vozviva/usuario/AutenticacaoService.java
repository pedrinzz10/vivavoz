package br.com.fiap.vozviva.usuario;

import br.com.fiap.vozviva.comum.erro.ConflitoException;
import br.com.fiap.vozviva.comum.erro.RecursoNaoEncontradoException;
import br.com.fiap.vozviva.seguranca.EmissorToken;
import br.com.fiap.vozviva.seguranca.EmissorToken.TokenEmitido;
import br.com.fiap.vozviva.usuario.dto.CadastroUsuarioRequest;
import br.com.fiap.vozviva.usuario.dto.LoginRequest;
import br.com.fiap.vozviva.usuario.dto.TokenResponse;
import br.com.fiap.vozviva.usuario.dto.UsuarioResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	/**
	 * Hash usado quando o e-mail não existe: a verificação de senha roda do mesmo jeito, para que o
	 * tempo de resposta não revele se a conta existe.
	 */
	private final String hashFicticio;

	public AutenticacaoService(UsuarioRepository usuarios, PasswordEncoder senhas, EmissorToken emissor,
			LimitadorTentativasLogin limitador) {
		this.usuarios = usuarios;
		this.senhas = senhas;
		this.emissor = emissor;
		this.limitador = limitador;
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

	static String normalizarEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
