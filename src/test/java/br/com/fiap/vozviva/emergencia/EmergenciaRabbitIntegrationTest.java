package br.com.fiap.vozviva.emergencia;

import br.com.fiap.vozviva.TestcontainersConfiguration;
import br.com.fiap.vozviva.emergencia.mensageria.RabbitEmergenciaConfig;
import br.com.fiap.vozviva.perfil.ContatoEmergencia;
import br.com.fiap.vozviva.perfil.ContatoEmergenciaRepository;
import br.com.fiap.vozviva.perfil.Mobilidade;
import br.com.fiap.vozviva.perfil.PerfilEmergencia;
import br.com.fiap.vozviva.perfil.PerfilEmergenciaRepository;
import br.com.fiap.vozviva.seguranca.Papel;
import br.com.fiap.vozviva.seguranca.TokenDeTeste;
import br.com.fiap.vozviva.usuario.Usuario;
import br.com.fiap.vozviva.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-008 ponta a ponta com RabbitMQ e SQL Server reais: o evento atravessa a fila e o consumidor
 * avisa os contatos; quando o envio falha em todas as tentativas, a mensagem vai para a DLQ.
 * Ignorado automaticamente sem Docker.
 */
@SpringBootTest(properties = {
		"spring.rabbitmq.listener.simple.auto-startup=true",
		"spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, EmergenciaRabbitIntegrationTest.NotificadorDeTeste.class })
@Testcontainers(disabledWithoutDocker = true)
class EmergenciaRabbitIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PerfilEmergenciaRepository perfis;

	@Autowired
	private ContatoEmergenciaRepository contatos;

	@Autowired
	private EmergenciaRepository emergencias;

	@Autowired
	private AvisoEmergenciaRepository avisos;

	@Autowired
	private RabbitTemplate rabbit;

	@Autowired
	private NotificadorDeTeste notificador;

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void eventoAtravessaAFilaEOsContatosSaoAvisados() throws Exception {
		Usuario cidadao = perfilComContato("Ana (filha)");

		long id = acionar(cidadao);

		Emergencia processada = aguardar(() -> emergencias.findById(id)
			.filter(e -> e.getStatus() == StatusEmergencia.NOTIFICADA).orElse(null));
		assertThat(processada.getContatosNotificados()).isEqualTo(1);
		assertThat(notificador.avisados).contains("Ana (filha)");
	}

	@Test
	void falhaEmTodasAsTentativasMandaAMensagemParaADlq() throws Exception {
		Usuario cidadao = perfilComContato(NotificadorDeTeste.CONTATO_QUE_FALHA);

		long id = acionar(cidadao);

		Message mortaNaDlq = aguardar(() -> rabbit.receive(RabbitEmergenciaConfig.FILA_DLQ, 500));
		assertThat(new String(mortaNaDlq.getBody(), StandardCharsets.UTF_8)).contains("\"emergenciaId\":" + id);
		assertThat(emergencias.findById(id).orElseThrow().getStatus()).isEqualTo(StatusEmergencia.ACIONADA);
	}

	/**
	 * Um contato que sempre falha não impede o aviso aos demais, e as novas tentativas não repetem
	 * o aviso de quem já recebeu (apontamento da revisão do PR #15).
	 */
	@Test
	void falhaDeUmContatoNaoBloqueiaNemRepeteOsOutros() throws Exception {
		String ana = "Ana " + UUID.randomUUID();
		Usuario cidadao = perfilComContato(NotificadorDeTeste.CONTATO_QUE_FALHA, ana);

		long id = acionar(cidadao);

		aguardar(() -> {
			Message morta = rabbit.receive(RabbitEmergenciaConfig.FILA_DLQ, 500);
			return morta != null && new String(morta.getBody(), StandardCharsets.UTF_8).contains("\"emergenciaId\":" + id) ? morta : null;
		});
		assertThat(notificador.avisados).filteredOn(ana::equals).hasSize(1);
		assertThat(avisos.findByEmergenciaIdOrderByIdAsc(id)).extracting(AvisoEmergencia::getContatoNome).containsExactly(ana);
		assertThat(emergencias.findById(id).orElseThrow().getStatus()).isEqualTo(StatusEmergencia.ACIONADA);
	}

	private Usuario perfilComContato(String... nomesContatos) {
		Usuario cidadao = usuarios.save(new Usuario("Maria", "c-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", Papel.CIDADAO));
		Usuario cuidador = usuarios.save(new Usuario("João", "k-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", Papel.CUIDADOR));
		PerfilEmergencia perfil = new PerfilEmergencia(cidadao, cuidador);
		perfil.atualizar(Mobilidade.CADEIRANTE, null, null, null);
		perfis.save(perfil);
		for (int i = 0; i < nomesContatos.length; i++) {
			ContatoEmergencia contato = new ContatoEmergencia(perfil);
			contato.atualizar(nomesContatos[i], "+551191234567" + i, "filha", i + 1);
			contatos.save(contato);
		}
		return cidadao;
	}

	private long acionar(Usuario cidadao) throws Exception {
		String corpo = mvc.perform(post("/api/v1/emergencias")
				.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao(cidadao.getId().toString()).bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"latitude\": -23.5329, \"longitude\": -46.7917}"))
			.andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString();
		return json.readTree(corpo).get("id").asLong();
	}

	private static <T> T aguardar(Supplier<T> condicao) throws InterruptedException {
		Instant limite = Instant.now().plus(Duration.ofSeconds(20));
		while (Instant.now().isBefore(limite)) {
			T valor = condicao.get();
			if (valor != null) {
				return valor;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("Condição não atendida em 20 segundos");
	}

	/**
	 * Substitui o notificador real: registra quem foi avisado e falha sempre para um contato específico.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class NotificadorDeTeste implements NotificadorContatos {

		static final String CONTATO_QUE_FALHA = "Contato com telefone fora do ar";

		final List<String> avisados = new CopyOnWriteArrayList<>();

		@Bean
		@Primary
		NotificadorContatos notificadorDeTeste() {
			return this;
		}

		@Override
		public void notificar(String nomeContato, String telefone, String mensagem) {
			if (CONTATO_QUE_FALHA.equals(nomeContato)) {
				throw new IllegalStateException("Falha simulada no envio");
			}
			avisados.add(nomeContato);
		}

	}

}
