package br.com.fiap.vozviva.perfil;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContatoEmergenciaRepository extends JpaRepository<ContatoEmergencia, Long> {

	Page<ContatoEmergencia> findByPerfilId(Long perfilId, Pageable pageable);

	/** Contatos na ordem em que devem ser avisados (usado pelo botão de emergência). */
	List<ContatoEmergencia> findByPerfilIdOrderByPrioridadeAscIdAsc(Long perfilId);

	Optional<ContatoEmergencia> findByIdAndPerfilId(Long id, Long perfilId);

	long countByPerfilId(Long perfilId);

}
