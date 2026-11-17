package br.com.fiap.vozviva.model;

/**
 * Órgão da prefeitura que o gestor atende na API .NET (zeladoria). Os valores são os mesmos de {@code AreaOrgao}
 * na API .NET, que usa a claim {@code orgao} do token para montar a fila do gestor.
 */
public enum AreaOrgao {

	OBRAS,

	ILUMINACAO,

	MOBILIDADE,

	LIMPEZA

}
