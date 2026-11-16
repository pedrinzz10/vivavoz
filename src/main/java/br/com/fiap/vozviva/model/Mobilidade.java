package br.com.fiap.vozviva.model;

/**
 * Como o cidadão se locomove ou percebe o ambiente. Orienta quem vai socorrer
 * (ex.: levar cadeira de rodas, falar de frente para uma pessoa surda).
 */
public enum Mobilidade {
	CADEIRANTE,
	ANDADOR,
	BENGALA,
	DEFICIENCIA_VISUAL,
	DEFICIENCIA_AUDITIVA,
	NENHUMA,
	/** A pessoa preferiu não informar (dado de saúde, LGPD). É o padrão quando o campo não vem. */
	NAO_INFORMADO
}
