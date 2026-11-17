package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.model.AreaOrgao;
import br.com.fiap.vozviva.model.Papel;
import br.com.fiap.vozviva.model.Usuario;
import br.com.fiap.vozviva.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Cria um cidadão, um cuidador e dois gestores (obras e iluminação) de teste para a demonstração e para o README.
 * Os gestores são a única forma de existir conta de gestor: o cadastro público não aceita esse papel. Só roda com
 * {@code vozviva.usuarios-teste.habilitado=true}, e a senha vem do ambiente (nunca do código).
 */
@Component
@ConditionalOnBooleanProperty("vozviva.usuarios-teste.habilitado")
public class UsuariosDeTeste implements ApplicationRunner {

	static final String EMAIL_CIDADAO = "cidadao.teste@vozviva.dev";
	static final String EMAIL_CUIDADOR = "cuidador.teste@vozviva.dev";
	static final String EMAIL_GESTOR_OBRAS = "gestor.obras@vozviva.dev";
	static final String EMAIL_GESTOR_ILUMINACAO = "gestor.iluminacao@vozviva.dev";

	private static final Logger log = LoggerFactory.getLogger(UsuariosDeTeste.class);

	private final UsuarioRepository usuarios;
	private final PasswordEncoder senhas;
	private final String senha;

	UsuariosDeTeste(UsuarioRepository usuarios, PasswordEncoder senhas,
			@Value("${vozviva.usuarios-teste.senha:}") String senha) {
		this.usuarios = usuarios;
		this.senhas = senhas;
		this.senha = senha;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(senha)) {
			log.warn("Usuários de teste habilitados, mas USUARIOS_TESTE_SENHA não foi definida. Nada foi criado.");
			return;
		}
		criarSeNaoExiste("Maria (cidadã de teste)", EMAIL_CIDADAO, Papel.CIDADAO);
		criarSeNaoExiste("João (cuidador de teste)", EMAIL_CUIDADOR, Papel.CUIDADOR);
		criarGestorSeNaoExiste("Carlos (gestor de obras de teste)", EMAIL_GESTOR_OBRAS, AreaOrgao.OBRAS);
		criarGestorSeNaoExiste("Ana (gestora de iluminação de teste)", EMAIL_GESTOR_ILUMINACAO, AreaOrgao.ILUMINACAO);
	}

	private void criarSeNaoExiste(String nome, String email, Papel papel) {
		if (!usuarios.existsByEmail(email)) {
			salvar(new Usuario(nome, email, senhas.encode(senha), papel));
		}
	}

	private void criarGestorSeNaoExiste(String nome, String email, AreaOrgao orgao) {
		if (!usuarios.existsByEmail(email)) {
			salvar(Usuario.gestor(nome, email, senhas.encode(senha), orgao));
		}
	}

	private void salvar(Usuario usuario) {
		usuario.registrarConsentimento(Instant.now());
		usuarios.save(usuario);
		log.info("Usuário de teste criado: {} ({})", usuario.getEmail(), usuario.getPapel());
	}

}
