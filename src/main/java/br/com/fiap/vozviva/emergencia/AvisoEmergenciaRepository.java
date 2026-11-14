package br.com.fiap.vozviva.emergencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvisoEmergenciaRepository extends JpaRepository<AvisoEmergencia, Long> {

	List<AvisoEmergencia> findByEmergenciaIdOrderByIdAsc(Long emergenciaId);

}
