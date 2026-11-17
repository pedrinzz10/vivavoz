package br.com.fiap.vozviva.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioTest {

	@Test
	void gestorSoNasceComOrgao() {
		Usuario gestor = Usuario.gestor("Carlos", "c@x.dev", "{bcrypt}hash", AreaOrgao.LIMPEZA);

		assertThat(gestor.getPapel()).isEqualTo(Papel.GESTOR);
		assertThat(gestor.getOrgao()).isEqualTo(AreaOrgao.LIMPEZA);
		assertThatThrownBy(() -> Usuario.gestor("Carlos", "c@x.dev", "{bcrypt}hash", null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void construtorComumNaoCriaGestorNemDaOrgao() {
		assertThatThrownBy(() -> new Usuario("Carlos", "c@x.dev", "{bcrypt}hash", Papel.GESTOR))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(new Usuario("Maria", "m@x.dev", "{bcrypt}hash", Papel.CIDADAO).getOrgao()).isNull();
	}

}
