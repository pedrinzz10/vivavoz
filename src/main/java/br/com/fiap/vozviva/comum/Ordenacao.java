package br.com.fiap.vozviva.comum;

import br.com.fiap.vozviva.comum.erro.RequisicaoInvalidaException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Restringe a ordenação das listagens a campos públicos. Sem isso, um cliente poderia ordenar por
 * campos internos de entidades relacionadas (ex.: {@code sort=cidadao.senhaHash}) e deduzir
 * informação pela ordem dos resultados.
 */
public final class Ordenacao {

	private Ordenacao() {
	}

	public static Pageable validar(Pageable pageable, Set<String> camposPermitidos) {
		for (Sort.Order ordem : pageable.getSort()) {
			if (!camposPermitidos.contains(ordem.getProperty())) {
				throw new RequisicaoInvalidaException("Não é possível ordenar por '" + ordem.getProperty()
						+ "'. Use: " + String.join(", ", camposPermitidos.stream().sorted().toList()) + ".");
			}
		}
		return pageable;
	}

}
