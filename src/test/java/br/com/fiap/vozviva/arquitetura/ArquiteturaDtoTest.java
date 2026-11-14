package br.com.fiap.vozviva.arquitetura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Garante o padrão DTO em todo o código de produção. Uma violação quebra o build.
 */
@AnalyzeClasses(packages = "br.com.fiap.vozviva", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaDtoTest {

	@ArchTest
	static final ArchRule dtosSaoRecords = RegrasDto.DTOS_SAO_RECORDS;

	@ArchTest
	static final ArchRule dtosTerminamEmRequestOuResponse = RegrasDto.DTOS_TERMINAM_EM_REQUEST_OU_RESPONSE;

	@ArchTest
	static final ArchRule requestEResponseFicamEmDto = RegrasDto.REQUEST_E_RESPONSE_FICAM_EM_DTO;

	@ArchTest
	static final ArchRule controllersSoDevolvemEReceberDtos = RegrasDto.CONTROLLERS_SO_DEVOLVEM_E_RECEBEM_DTOS;

	@ArchTest
	static final ArchRule controllersNaoAcessamEntidades = RegrasDto.CONTROLLERS_NAO_ACESSAM_ENTIDADES_NEM_REPOSITORIOS;

	@ArchTest
	static final ArchRule dtosNaoDependemDeEntidades = RegrasDto.DTOS_NAO_DEPENDEM_DE_ENTIDADES;

}
