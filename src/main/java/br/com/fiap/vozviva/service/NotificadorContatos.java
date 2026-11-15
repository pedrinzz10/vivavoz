package br.com.fiap.vozviva.service;

/**
 * Canal que entrega o aviso de emergência a um contato (SMS, WhatsApp, push...). Ficar atrás de uma
 * interface permite trocar o canal sem mexer no fluxo de emergência.
 */
public interface NotificadorContatos {

	/**
	 * @throws RuntimeException se o aviso não puder ser entregue (a mensagem volta para a fila e é
	 *                          tentada de novo; depois das tentativas, vai para a DLQ)
	 */
	void notificar(String nomeContato, String telefone, String mensagem);

}
