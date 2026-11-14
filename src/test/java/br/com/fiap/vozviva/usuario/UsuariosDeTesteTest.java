package br.com.fiap.vozviva.usuario;

import br.com.fiap.vozviva.seguranca.Papel;
import br.com.fiap.vozviva.usuario.dto.CadastroUsuarioRequest;
import br.com.fiap.vozviva.usuario.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuariosDeTesteTest {

	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

	@Test
	void criaCidadaoECuidadorQuandoHaSenha() {
		when(usuarios.existsByEmail(any())).thenReturn(false);

		new UsuariosDeTeste(usuarios, PasswordEncoderFactories.createDelegatingPasswordEncoder(), "SenhaDeTeste1")
			.run(null);

		ArgumentCaptor<Usuario> salvos = ArgumentCaptor.forClass(Usuario.class);
		verify(usuarios, times(2)).save(salvos.capture());
		assertThat(salvos.getAllValues()).extracting(Usuario::getPapel).containsExactly(Papel.CIDADAO, Papel.CUIDADOR);
		assertThat(salvos.getAllValues()).extracting(Usuario::getEmail)
			.containsExactly(UsuariosDeTeste.EMAIL_CIDADAO, UsuariosDeTeste.EMAIL_CUIDADOR);
		assertThat(salvos.getAllValues()).allSatisfy(u -> assertThat(u.getSenhaHash()).startsWith("{bcrypt}"));
	}

	@Test
	void naoCriaNadaSemSenhaDefinida() {
		new UsuariosDeTeste(usuarios, PasswordEncoderFactories.createDelegatingPasswordEncoder(), "").run(null);

		verify(usuarios, never()).save(any());
	}

	@Test
	void naoDuplicaUsuariosExistentes() {
		when(usuarios.existsByEmail(any())).thenReturn(true);

		new UsuariosDeTeste(usuarios, PasswordEncoderFactories.createDelegatingPasswordEncoder(), "SenhaDeTeste1")
			.run(null);

		verify(usuarios, never()).save(any());
	}

	@Test
	void senhaNaoApareceNoToStringDosDtos() {
		assertThat(new CadastroUsuarioRequest("Maria", "m@x.com", "SenhaSecreta1", Papel.CIDADAO).toString())
			.doesNotContain("SenhaSecreta1");
		assertThat(new LoginRequest("m@x.com", "SenhaSecreta1").toString()).doesNotContain("SenhaSecreta1");
	}

}
