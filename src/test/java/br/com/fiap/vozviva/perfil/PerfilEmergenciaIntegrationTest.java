package br.com.fiap.vozviva.perfil;

import br.com.fiap.vozviva.seguranca.Papel;
import br.com.fiap.vozviva.seguranca.TokenDeTeste;
import br.com.fiap.vozviva.usuario.Usuario;
import br.com.fiap.vozviva.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-017: CRUD paginado do perfil de emergência e dos contatos, com as regras de acesso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PerfilEmergenciaIntegrationTest {

	private static final String PERFIS = "/api/v1/perfis-emergencia";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	private final JsonMapper json = JsonMapper.builder().build();

	private Usuario cidadao;
	private Usuario cuidador;
	private Usuario outroCuidador;

	@BeforeEach
	void criarUsuarios() {
		cidadao = usuario("Maria", Papel.CIDADAO);
		cuidador = usuario("João", Papel.CUIDADOR);
		outroCuidador = usuario("Pedro", Papel.CUIDADOR);
	}

	// --- Perfil -------------------------------------------------------------

	@Test
	void crudCompletoDoPerfil() throws Exception {
		MvcResult criado = mvc.perform(como(cuidador, post(PERFIS)).content(perfilJson(cidadao.getEmail(), "CADEIRANTE")))
			.andExpect(status().isCreated())
			.andReturn();
		long id = id(criado);
		assertThat(criado.getResponse().getHeader(HttpHeaders.LOCATION))
			.endsWith(PERFIS + "/" + id);

		mvc.perform(como(cuidador, get(PERFIS + "/" + id)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cidadao.nome").value("Maria"))
			.andExpect(jsonPath("$.cuidador.nome").value("João"))
			.andExpect(jsonPath("$.mobilidade").value("CADEIRANTE"))
			.andExpect(jsonPath("$.cidadao.email").doesNotExist());

		mvc.perform(como(cuidador, put(PERFIS + "/" + id)).content("""
				{"mobilidade": "ANDADOR", "medicamentos": "Losartana 50mg"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mobilidade").value("ANDADOR"))
			.andExpect(jsonPath("$.medicamentos").value("Losartana 50mg"));

		mvc.perform(como(cuidador, delete(PERFIS + "/" + id))).andExpect(status().isNoContent());
		mvc.perform(como(cuidador, get(PERFIS + "/" + id))).andExpect(status().isNotFound());
	}

	@Test
	void cidadaoConsultaOProprioPerfilMasNaoAltera() throws Exception {
		long id = criarPerfil(cuidador, cidadao);

		mvc.perform(como(cidadao, get(PERFIS + "/meu")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id));
		mvc.perform(como(cidadao, get(PERFIS + "/" + id))).andExpect(status().isOk());

		mvc.perform(como(cidadao, put(PERFIS + "/" + id)).content("{\"mobilidade\": \"NENHUMA\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(como(cidadao, delete(PERFIS + "/" + id))).andExpect(status().isForbidden());
		mvc.perform(como(cidadao, post(PERFIS)).content(perfilJson(cidadao.getEmail(), "NENHUMA")))
			.andExpect(status().isForbidden());
	}

	@Test
	void cidadaoSemPerfilRecebe404NoMeu() throws Exception {
		mvc.perform(como(cidadao, get(PERFIS + "/meu")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.mensagemFalada").isNotEmpty());
	}

	@Test
	void outroCuidadorNaoVeNemAlteraOPerfil() throws Exception {
		long id = criarPerfil(cuidador, cidadao);

		mvc.perform(como(outroCuidador, get(PERFIS + "/" + id))).andExpect(status().isForbidden());
		mvc.perform(como(outroCuidador, put(PERFIS + "/" + id)).content("{\"mobilidade\": \"NENHUMA\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
		mvc.perform(como(outroCuidador, delete(PERFIS + "/" + id))).andExpect(status().isForbidden());
		mvc.perform(como(outroCuidador, get(PERFIS + "/" + id + "/contatos"))).andExpect(status().isForbidden());
	}

	@Test
	void outroCidadaoNaoVeOPerfil() throws Exception {
		long id = criarPerfil(cuidador, cidadao);
		Usuario vizinho = usuario("Vizinho", Papel.CIDADAO);

		mvc.perform(como(vizinho, get(PERFIS + "/" + id))).andExpect(status().isForbidden());
	}

	@Test
	void perfilDuplicadoParaOMesmoCidadao409() throws Exception {
		criarPerfil(cuidador, cidadao);

		mvc.perform(como(outroCuidador, post(PERFIS)).content(perfilJson(cidadao.getEmail(), "BENGALA")))
			.andExpect(status().isConflict());
	}

	@Test
	void emailQueNaoEDeCidadao400EInexistente404() throws Exception {
		mvc.perform(como(cuidador, post(PERFIS)).content(perfilJson(outroCuidador.getEmail(), "NENHUMA")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
		mvc.perform(como(cuidador, post(PERFIS)).content(perfilJson("ninguem-" + UUID.randomUUID() + "@x.com", "NENHUMA")))
			.andExpect(status().isNotFound());
	}

	@Test
	void listagemDoCuidadorEPaginadaESoTrazOsDele() throws Exception {
		for (int i = 0; i < 3; i++) {
			criarPerfil(cuidador, usuario("Cidadão " + i, Papel.CIDADAO));
		}
		criarPerfil(outroCuidador, usuario("De outro", Papel.CIDADAO));

		mvc.perform(como(cuidador, get(PERFIS).param("page", "0").param("size", "2")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conteudo", hasSize(2)))
			.andExpect(jsonPath("$.totalElementos").value(3))
			.andExpect(jsonPath("$.totalPaginas").value(2))
			.andExpect(jsonPath("$.ultima").value(false));
	}

	// --- Contatos -----------------------------------------------------------

	@Test
	void crudCompletoDoContatoComTelefoneNormalizado() throws Exception {
		long perfil = criarPerfil(cuidador, cidadao);
		String contatos = PERFIS + "/" + perfil + "/contatos";

		MvcResult criado = mvc.perform(como(cuidador, post(contatos)).content(contatoJson("Ana (filha)", "(11) 91234-5678", 1)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.telefone").value("+5511912345678"))
			.andReturn();
		long contato = id(criado);

		mvc.perform(como(cidadao, get(contatos + "/" + contato)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nome").value("Ana (filha)"));

		mvc.perform(como(cuidador, put(contatos + "/" + contato)).content(contatoJson("Ana Souza", "11 3456-7890", 2)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.telefone").value("+551134567890"))
			.andExpect(jsonPath("$.prioridade").value(2));

		mvc.perform(como(cuidador, delete(contatos + "/" + contato))).andExpect(status().isNoContent());
		mvc.perform(como(cuidador, get(contatos + "/" + contato))).andExpect(status().isNotFound());
	}

	@Test
	void paginacaoEOrdenacaoDosContatos() throws Exception {
		long perfil = criarPerfil(cuidador, cidadao);
		String contatos = PERFIS + "/" + perfil + "/contatos";
		String[] nomes = { "Carla", "Ana", "Bruno", "Diego", "Eva", "Fabio", "Gil" };
		for (int i = 0; i < nomes.length; i++) {
			mvc.perform(como(cuidador, post(contatos)).content(contatoJson(nomes[i], "11912345678", i + 1)))
				.andExpect(status().isCreated());
		}

		mvc.perform(como(cuidador, get(contatos).param("page", "1").param("size", "3").param("sort", "nome,asc")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conteudo[*].nome", contains("Diego", "Eva", "Fabio")))
			.andExpect(jsonPath("$.pagina").value(1))
			.andExpect(jsonPath("$.tamanho").value(3))
			.andExpect(jsonPath("$.totalElementos").value(7))
			.andExpect(jsonPath("$.totalPaginas").value(3));

		mvc.perform(como(cidadao, get(contatos)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conteudo[0].nome").value("Carla"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "senhaHash", "perfil.cidadao.senhaHash", "telefone" })
	void ordenacaoPorCampoNaoPermitido400(String campo) throws Exception {
		long perfil = criarPerfil(cuidador, cidadao);

		mvc.perform(como(cuidador, get(PERFIS + "/" + perfil + "/contatos").param("sort", campo)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
	}

	@Test
	void ordenacaoInvalidaNaListagemDePerfis400() throws Exception {
		mvc.perform(como(cuidador, get(PERFIS).param("sort", "cidadao.senhaHash")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void contatoComTelefoneInvalido400() throws Exception {
		long perfil = criarPerfil(cuidador, cidadao);

		mvc.perform(como(cuidador, post(PERFIS + "/" + perfil + "/contatos")).content(contatoJson("Ana", "123", 1)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("VALIDACAO"))
			.andExpect(jsonPath("$.campos[0].campo").value("telefone"));
	}

	@Test
	void limiteDeDezContatos() throws Exception {
		long perfil = criarPerfil(cuidador, cidadao);
		String contatos = PERFIS + "/" + perfil + "/contatos";
		for (int i = 1; i <= PerfilEmergenciaService.MAXIMO_CONTATOS; i++) {
			mvc.perform(como(cuidador, post(contatos)).content(contatoJson("Contato " + i, "11912345678", i)))
				.andExpect(status().isCreated());
		}

		mvc.perform(como(cuidador, post(contatos)).content(contatoJson("Excedente", "11912345678", 1)))
			.andExpect(status().isConflict());
	}

	@Test
	void contatoDeOutroPerfil404() throws Exception {
		long perfilMaria = criarPerfil(cuidador, cidadao);
		long perfilOutro = criarPerfil(cuidador, usuario("Outra", Papel.CIDADAO));
		long contato = id(mvc.perform(como(cuidador, post(PERFIS + "/" + perfilMaria + "/contatos"))
			.content(contatoJson("Ana", "11912345678", 1))).andReturn());

		mvc.perform(como(cuidador, get(PERFIS + "/" + perfilOutro + "/contatos/" + contato)))
			.andExpect(status().isNotFound());
	}

	// --- Apoio --------------------------------------------------------------

	private Usuario usuario(String nome, Papel papel) {
		return usuarios.save(new Usuario(nome, "u-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", papel));
	}

	private long criarPerfil(Usuario dono, Usuario de) throws Exception {
		return id(mvc.perform(como(dono, post(PERFIS)).content(perfilJson(de.getEmail(), "CADEIRANTE")))
			.andExpect(status().isCreated())
			.andReturn());
	}

	private static MockHttpServletRequestBuilder como(Usuario usuario, MockHttpServletRequestBuilder requisicao) {
		TokenDeTeste token = usuario.getPapel() == Papel.CUIDADOR
				? TokenDeTeste.cuidador(usuario.getId().toString())
				: TokenDeTeste.cidadao(usuario.getId().toString());
		return requisicao.header(HttpHeaders.AUTHORIZATION, token.bearer()).contentType(MediaType.APPLICATION_JSON);
	}

	private static String perfilJson(String email, String mobilidade) {
		return """
				{"cidadaoEmail": "%s", "mobilidade": "%s", "condicoesSaude": "Hipertensão"}""".formatted(email, mobilidade);
	}

	private static String contatoJson(String nome, String telefone, int prioridade) {
		return """
				{"nome": "%s", "telefone": "%s", "parentesco": "filha", "prioridade": %d}""".formatted(nome, telefone, prioridade);
	}

	private long id(MvcResult resultado) throws Exception {
		return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
	}

}
