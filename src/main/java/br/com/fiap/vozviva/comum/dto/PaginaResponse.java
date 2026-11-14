package br.com.fiap.vozviva.comum.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados com os dados de navegação. A primeira página é a 0.
 */
public record PaginaResponse<T>(
		List<T> conteudo,
		int pagina,
		int tamanho,
		long totalElementos,
		int totalPaginas,
		boolean ultima) {

	public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> conversor) {
		return new PaginaResponse<>(
				pagina.getContent().stream().map(conversor).toList(),
				pagina.getNumber(),
				pagina.getSize(),
				pagina.getTotalElements(),
				pagina.getTotalPages(),
				pagina.isLast());
	}

}
