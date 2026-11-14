package br.com.fiap.exemplosarquitetura.ruim.dto;

/**
 * DTO fora do padrão (classe mutável em vez de record), usado só nos testes de arquitetura.
 */
public class ClasseMutavelResponse {

	private String valor;

	public String getValor() {
		return valor;
	}

	public void setValor(String valor) {
		this.valor = valor;
	}

}
