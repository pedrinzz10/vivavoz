package br.com.fiap.vozviva.atendimento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface PontoAtendimentoRepository extends JpaRepository<PontoAtendimento, Long> {

	/** Pré-filtro por área (usa o índice tipo + latitude + longitude); a distância exata é calculada depois. */
	List<PontoAtendimento> findByTipoAndLatitudeBetweenAndLongitudeBetween(TipoAtendimento tipo,
			BigDecimal latitudeMinima, BigDecimal latitudeMaxima, BigDecimal longitudeMinima, BigDecimal longitudeMaxima);

	Page<PontoAtendimento> findByTipo(TipoAtendimento tipo, Pageable pageable);

}
