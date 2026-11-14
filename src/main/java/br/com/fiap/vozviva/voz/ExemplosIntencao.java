package br.com.fiap.vozviva.voz;

import br.com.fiap.vozviva.comum.busca.IndiceTfIdf;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Parte de recuperação do RAG da classificação por voz: os exemplos curados mais parecidos com a fala
 * recebida ({@code conhecimento/exemplos-intencoes.json}) vão no prompt e orientam o modelo.
 */
@Component
public class ExemplosIntencao {

	static final String ARQUIVO = "conhecimento/exemplos-intencoes.json";

	private final IndiceTfIdf<Exemplo> indice;

	public ExemplosIntencao(ObjectMapper json) {
		this.indice = new IndiceTfIdf<>(carregar(json), Exemplo::fala);
	}

	public List<Exemplo> parecidos(String fala, int limite) {
		return indice.buscar(fala, limite);
	}

	public int tamanho() {
		return indice.tamanho();
	}

	private static List<Exemplo> carregar(ObjectMapper json) {
		try (InputStream entrada = new ClassPathResource(ARQUIVO).getInputStream()) {
			return List.copyOf(json.readValue(entrada, Arquivo.class).exemplos());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Não foi possível ler " + ARQUIVO, ex);
		}
	}

	record Arquivo(String descricao, String versao, List<Exemplo> exemplos) {
	}

	public record Exemplo(String fala, Intencao intencao, Urgencia urgencia, EntidadesFala entidades) {
	}

}
