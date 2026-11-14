package br.com.fiap.vozviva.comum.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class LimiteBytesUtf8Validator implements ConstraintValidator<LimiteBytesUtf8, String> {

	private int max;

	@Override
	public void initialize(LimiteBytesUtf8 anotacao) {
		this.max = anotacao.max();
	}

	@Override
	public boolean isValid(String valor, ConstraintValidatorContext contexto) {
		return valor == null || valor.getBytes(StandardCharsets.UTF_8).length <= max;
	}

}
