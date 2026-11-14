package br.com.fiap.vozviva.usuario;

import br.com.fiap.vozviva.seguranca.Papel;
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

/**
 * Cria um cidadão e um cuidador de teste para a demonstração e para o README. Só roda com
 * {@code vozviva.usuarios-teste.habilitado=true}, e a senha vem do ambiente (nunca do código).
 */
@Component
@ConditionalOnBooleanProperty("vozviva.usuarios-teste.habilitado")
class UsuariosDeTeste implements ApplicationRunner {

	static final String EMAIL_CIDADAO = "cidadao.teste@vozviva.dev";
	static final String EMAIL_CUIDADOR = "cuidador.teste@vozviva.dev";

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
	}

	private void criarSeNaoExiste(String nome, String email, Papel papel) {
		if (!usuarios.existsByEmail(email)) {
			usuarios.save(new Usuario(nome, email, senhas.encode(senha), papel));
			log.info("Usuário de teste criado: {} ({})", email, papel);
		}
	}

}
