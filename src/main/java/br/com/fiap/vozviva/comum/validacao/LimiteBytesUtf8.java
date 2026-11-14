package br.com.fiap.vozviva.comum.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limita o tamanho do texto em bytes UTF-8 (e não em caracteres). Necessário para senhas: o BCrypt
 * aceita no máximo 72 bytes, e letras acentuadas ocupam 2 bytes cada.
 */
@Documented
@Constraint(validatedBy = LimiteBytesUtf8Validator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface LimiteBytesUtf8 {

	int max();

	String message() default "é longo demais (no máximo {max} bytes; letras acentuadas contam como 2)";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
