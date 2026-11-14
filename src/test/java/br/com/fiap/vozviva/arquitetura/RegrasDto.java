package br.com.fiap.vozviva.arquitetura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.Repository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Regras do padrão DTO do projeto:
 * <ul>
 *   <li>DTOs ficam em subpacotes {@code dto}, são records imutáveis e terminam em Request ou Response;</li>
 *   <li>controllers só recebem e devolvem DTOs (nunca entidades, Map ou Object);</li>
 *   <li>entidades e repositórios nunca chegam aos controllers nem aos DTOs.</li>
 * </ul>
 */
final class RegrasDto {

	static final String PACOTE_DTO = "..dto..";

	/** Tipos que podem envolver um DTO na resposta de um endpoint. */
	private static final Set<String> TIPOS_ENVELOPE = Set.of(
			"void", Void.class.getName(), ResponseEntity.class.getName(),
			List.class.getName(), Set.class.getName(), Collection.class.getName());

	static final ArchRule DTOS_SAO_RECORDS = classes()
		.that().resideInAPackage(PACOTE_DTO)
		.and().doNotHaveSimpleName("package-info")
		.should(serRecord())
		.as("DTOs devem ser records (imutáveis)");

	static final ArchRule DTOS_TERMINAM_EM_REQUEST_OU_RESPONSE = classes()
		.that().resideInAPackage(PACOTE_DTO)
		.and().areTopLevelClasses()
		.and().doNotHaveSimpleName("package-info")
		.should().haveSimpleNameEndingWith("Request")
		.orShould().haveSimpleNameEndingWith("Response")
		.as("DTOs devem terminar em Request (entrada) ou Response (saída)");

	static final ArchRule REQUEST_E_RESPONSE_FICAM_EM_DTO = classes()
		.that().haveSimpleNameEndingWith("Request")
		.or().haveSimpleNameEndingWith("Response")
		.should().resideInAPackage(PACOTE_DTO)
		.as("Classes *Request e *Response devem ficar em um subpacote dto");

	static final ArchRule CONTROLLERS_SO_DEVOLVEM_E_RECEBEM_DTOS = methods()
		.that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
		.and().areMetaAnnotatedWith(RequestMapping.class)
		.should(usarApenasDtos())
		.allowEmptyShould(true)
		.as("Endpoints devem receber (@RequestBody) e devolver apenas DTOs");

	static final ArchRule CONTROLLERS_NAO_ACESSAM_ENTIDADES_NEM_REPOSITORIOS = noClasses()
		.that().areAnnotatedWith(RestController.class)
		.should().dependOnClassesThat().areAnnotatedWith(Document.class)
		.orShould().dependOnClassesThat().areAssignableTo(Repository.class)
		.allowEmptyShould(true)
		.as("Controllers não devem acessar entidades nem repositórios (use serviços e DTOs)");

	static final ArchRule DTOS_NAO_DEPENDEM_DE_ENTIDADES = noClasses()
		.that().resideInAPackage(PACOTE_DTO)
		.should().dependOnClassesThat().areAnnotatedWith(Document.class)
		.orShould().dependOnClassesThat().areAssignableTo(Repository.class)
		.as("DTOs não devem depender de entidades nem de repositórios");

	private RegrasDto() {
	}

	private static ArchCondition<JavaClass> serRecord() {
		return new ArchCondition<>("ser record") {
			@Override
			public void check(JavaClass classe, ConditionEvents eventos) {
				boolean record = classe.isAssignableTo(Record.class);
				eventos.add(new SimpleConditionEvent(classe, record,
						classe.getName() + (record ? " é" : " não é") + " um record"));
			}
		};
	}

	private static ArchCondition<JavaMethod> usarApenasDtos() {
		return new ArchCondition<>("usar apenas DTOs") {
			@Override
			public void check(JavaMethod metodo, ConditionEvents eventos) {
				for (JavaClass tipo : metodo.getReturnType().getAllInvolvedRawTypes()) {
					if (!TIPOS_ENVELOPE.contains(tipo.getName()) && !ehDto().test(tipo)) {
						eventos.add(SimpleConditionEvent.violated(metodo, metodo.getFullName()
								+ " devolve " + tipo.getName() + ", que não é um DTO"));
					}
				}
				for (JavaParameter parametro : metodo.getParameters()) {
					if (parametro.isAnnotatedWith(RequestBody.class) && !ehDto().test(parametro.getRawType())) {
						eventos.add(SimpleConditionEvent.violated(metodo, metodo.getFullName()
								+ " recebe " + parametro.getRawType().getName() + " no corpo, que não é um DTO"));
					}
				}
			}
		};
	}

	private static DescribedPredicate<JavaClass> ehDto() {
		return DescribedPredicate.describe("DTO", classe -> classe.getPackageName().matches(".*\\.dto(\\..*)?")
				&& classe.isAssignableTo(Record.class));
	}

}
