package br.com.fiap.vozviva.emergencia;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmergenciaRepository extends JpaRepository<Emergencia, Long> {

	@EntityGraph(attributePaths = { "perfil", "perfil.cidadao", "perfil.cuidador" })
	Optional<Emergencia> findWithPerfilById(Long id);

	/**
	 * Leitura com trava da linha até o fim da transação: se dois consumidores pegarem a mesma
	 * emergência ao mesmo tempo, o segundo espera e encontra o status já atualizado.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Emergencia e join fetch e.perfil p join fetch p.cidadao where e.id = :id")
	Optional<Emergencia> findParaProcessarById(@Param("id") Long id);

	@EntityGraph(attributePaths = { "perfil", "perfil.cidadao" })
	Page<Emergencia> findByPerfilCidadaoId(Long cidadaoId, Pageable pageable);

	@EntityGraph(attributePaths = { "perfil", "perfil.cidadao" })
	Page<Emergencia> findByPerfilCuidadorId(Long cuidadorId, Pageable pageable);

	/** Emergência ainda aberta acionada há pouco tempo (proteção contra toque duplo). */
	Optional<Emergencia> findFirstByPerfilIdAndStatusInAndAcionadaEmAfterOrderByAcionadaEmDesc(
			Long perfilId, Collection<StatusEmergencia> status, Instant desde);

	/** Emergências que ficaram sem processar (ex.: o broker estava fora do ar na hora). */
	List<Emergencia> findTop50ByStatusAndAcionadaEmBeforeOrderByAcionadaEmAsc(StatusEmergencia status, Instant antesDe);

}
