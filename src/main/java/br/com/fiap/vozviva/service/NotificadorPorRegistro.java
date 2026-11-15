package br.com.fiap.vozviva.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação padrão (sem custo): registra o aviso no log. O envio real por SMS ou WhatsApp entra
 * como outra implementação de {@link NotificadorContatos}. O telefone aparece mascarado no log (LGPD).
 */
@Component
public class NotificadorPorRegistro implements NotificadorContatos {

	private static final Logger log = LoggerFactory.getLogger(NotificadorPorRegistro.class);

	@Override
	public void notificar(String nomeContato, String telefone, String mensagem) {
		log.info("Aviso de emergência enviado para {} ({})", nomeContato, mascarar(telefone));
	}

	static String mascarar(String telefone) {
		if (telefone == null || telefone.length() <= 8) {
			return "****";
		}
		return telefone.substring(0, 5) + "*".repeat(telefone.length() - 9) + telefone.substring(telefone.length() - 4);
	}

}
