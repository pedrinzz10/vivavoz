package br.com.fiap.vozviva.arquitetura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova que as regras de arquitetura reprovam código fora do padrão DTO e aprovam código correto.
 */
class RegrasDtoTest {

	private static final JavaClasses FORA_DO_PADRAO =
			new ClassFileImporter().importPackages("br.com.fiap.exemplosarquitetura.ruim");

	private static final JavaClasses NO_PADRAO =
			new ClassFileImporter().importPackages("br.com.fiap.exemplosarquitetura.bom");

	@Test
	void reprovaControllerQueDevolveMapOuEntidadeOuRecebeEntidade() {
		assertThatThrownBy(() -> RegrasDto.CONTROLLERS_SO_DEVOLVEM_E_RECEBEM_DTOS.check(FORA_DO_PADRAO))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("devolveMapa")
			.hasMessageContaining("devolveEntidades")
			.hasMessageContaining("recebeEntidade");
	}

	@Test
	void reprovaControllerQueDependeDeEntidade() {
		assertThatThrownBy(() -> RegrasDto.CONTROLLERS_NAO_ACESSAM_ENTIDADES_NEM_REPOSITORIOS.check(FORA_DO_PADRAO))
			.isInstanceOf(AssertionError.class);
	}

	@Test
	void reprovaDtoQueNaoERecord() {
		assertThatThrownBy(() -> RegrasDto.DTOS_SAO_RECORDS.check(FORA_DO_PADRAO))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("ClasseMutavelResponse");
	}

	@Test
	void reprovaDtoSemSufixoRequestOuResponse() {
		assertThatThrownBy(() -> RegrasDto.DTOS_TERMINAM_EM_REQUEST_OU_RESPONSE.check(FORA_DO_PADRAO))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("Obstrucao");
	}

	@Test
	void aprovaControllerEDtosNoPadrao() {
		for (ArchRule regra : new ArchRule[] {
				RegrasDto.CONTROLLERS_SO_DEVOLVEM_E_RECEBEM_DTOS,
				RegrasDto.CONTROLLERS_NAO_ACESSAM_ENTIDADES_NEM_REPOSITORIOS,
				RegrasDto.DTOS_SAO_RECORDS,
				RegrasDto.DTOS_TERMINAM_EM_REQUEST_OU_RESPONSE,
				RegrasDto.REQUEST_E_RESPONSE_FICAM_EM_DTO,
				RegrasDto.DTOS_NAO_DEPENDEM_DE_ENTIDADES }) {
			assertThatCode(() -> regra.check(NO_PADRAO)).as(regra.getDescription()).doesNotThrowAnyException();
		}
	}

}
