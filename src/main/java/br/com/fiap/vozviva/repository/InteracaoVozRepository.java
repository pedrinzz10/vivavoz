package br.com.fiap.vozviva.repository;

import br.com.fiap.vozviva.model.InteracaoVoz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface InteracaoVozRepository extends JpaRepository<InteracaoVoz, Long> {

	Page<InteracaoVoz> findByUsuarioId(Long usuarioId, Pageable pageable);

	/** As falas mais recentes da pessoa, da mais nova para a mais antiga (contexto da conversa por voz). */
	List<InteracaoVoz> findTop3ByUsuarioIdAndCriadaEmAfterOrderByCriadaEmDesc(Long usuarioId, Instant desde);

}
