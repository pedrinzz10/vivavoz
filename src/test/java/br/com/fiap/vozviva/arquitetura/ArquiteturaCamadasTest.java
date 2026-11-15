package br.com.fiap.vozviva.arquitetura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.Entity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Organização em camadas: controller → service → repository, com as entidades em model e os DTOs em dto.
 * Cada classe fica na pasta da sua camada, e uma camada só usa as de baixo. Uma violação quebra o build.
 */
@AnalyzeClasses(packages = "br.com.fiap.vozviva", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaCamadasTest {

	private static final String BASE = "br.com.fiap.vozviva";

	@ArchTest
	static final ArchRule camadas = layeredArchitecture()
		.consideringOnlyDependenciesInLayers()
		.layer("Controller").definedBy(BASE + ".controller..")
		.layer("Service").definedBy(BASE + ".service..")
		.layer("Repository").definedBy(BASE + ".repository..")
		.layer("Mensageria").definedBy(BASE + ".messaging..")
		.whereLayer("Controller").mayNotBeAccessedByAnyLayer()
		// O consumidor da fila é uma porta de entrada assíncrona, como um controller.
		.whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Mensageria")
		.whereLayer("Repository").mayOnlyBeAccessedByLayers("Service", "Mensageria")
		.as("Camadas: controller → service → repository (a mensageria entra pelos serviços)");

	@ArchTest
	static final ArchRule controllersNaPastaController = classes()
		.that().areAnnotatedWith(RestController.class)
		.should().resideInAPackage(BASE + ".controller")
		.andShould().haveSimpleNameEndingWith("Controller")
		.as("Controllers ficam em controller e terminam em Controller");

	@ArchTest
	static final ArchRule servicosNaPastaService = classes()
		.that().areAnnotatedWith(Service.class)
		.should().resideInAPackage(BASE + ".service")
		.as("Serviços (@Service) ficam em service");

	@ArchTest
	static final ArchRule repositoriosNaPastaRepository = classes()
		.that().areAssignableTo(Repository.class)
		.should().resideInAPackage(BASE + ".repository")
		.andShould().haveSimpleNameEndingWith("Repository")
		.as("Repositórios ficam em repository e terminam em Repository");

	@ArchTest
	static final ArchRule entidadesNaPastaModel = classes()
		.that().areAnnotatedWith(Entity.class)
		.should().resideInAPackage(BASE + ".model")
		.as("Entidades JPA ficam em model");

}
