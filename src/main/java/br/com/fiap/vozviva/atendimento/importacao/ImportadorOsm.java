package br.com.fiap.vozviva.atendimento.importacao;

import br.com.fiap.vozviva.atendimento.Acessibilidade;
import br.com.fiap.vozviva.atendimento.TipoAtendimento;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Importa os locais de atendimento da região de Osasco/SP do OpenStreetMap (Overpass API) e gera a
 * migração repetível do Flyway com a carga ({@code R__carga_pontos_atendimento.sql}).
 * <p>
 * Ferramenta de linha de comando, fora da aplicação: rode {@code ./gradlew importarOsm} (consulta a
 * Overpass uma vez) ou {@code ./gradlew importarOsm -Pentrada=arquivo.json} (a partir de uma resposta salva).
 */
public final class ImportadorOsm {

	/** Osasco/SP com margem para os bairros vizinhos de São Paulo, Carapicuíba e Barueri (sul, oeste, norte, leste). */
	static final String AREA = "-23.600,-46.850,-23.460,-46.700";
	/** Servidor principal e espelhos públicos, tentados em ordem (o principal recusa pedidos quando está lotado). */
	static final List<String> SERVIDORES_OVERPASS = List.of("https://overpass-api.de/api/interpreter",
			"https://overpass.private.coffee/api/interpreter", "https://overpass.kumi.systems/api/interpreter");
	static final String FONTE = "OpenStreetMap (ODbL)";
	/** Mesmo local mapeado duas vezes (ex.: ponto e contorno do prédio) a menos disso vira um só. */
	private static final double DISTANCIA_DUPLICATA_METROS = 200;
	private static final int LINHAS_POR_INSERT = 500;
	private static final List<String> COLUNAS_ATUALIZADAS = List.of("nome", "tipo", "endereco", "telefone", "latitude",
			"longitude", "acessibilidade", "banheiro_adaptado", "funciona_24h", "fonte", "atualizado_em");
	private static final List<String> TODAS_AS_COLUNAS = Stream.concat(Stream.of("osm_id"), COLUNAS_ATUALIZADAS.stream()).toList();
	private static final String COLUNAS = String.join(", ", TODAS_AS_COLUNAS);

	static final String CONSULTA = """
			[out:json][timeout:90][bbox:%s];
			(
			  nwr["amenity"~"^(hospital|clinic|doctors|pharmacy|police|social_facility)$"];
			  nwr["healthcare"];
			  nwr["name"~"CRAS|Defensoria|Assist[eê]ncia Social",i];
			);
			out center tags;
			""".formatted(AREA);

	private ImportadorOsm() {
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length < 1) {
			throw new IllegalArgumentException("Uso: ImportadorOsm <arquivo-sql-de-saida> [resposta-overpass.json]");
		}
		String json = args.length > 1 ? Files.readString(Path.of(args[1])) : consultarOverpass();
		JsonNode resposta = JsonMapper.builder().build().readTree(json);
		if (!resposta.path("elements").isArray()) {
			throw new IOException("Resposta da Overpass sem a lista de elementos: a carga atual não será substituída.");
		}
		List<Ponto> pontos = converter(resposta);
		Instant dataBase = dataDosDados(resposta);
		Files.writeString(Path.of(args[0]), gerarSql(pontos, dataBase), StandardCharsets.UTF_8);
		Map<TipoAtendimento, Long> porTipo = new LinkedHashMap<>();
		pontos.forEach(p -> porTipo.merge(p.tipo(), 1L, Long::sum));
		System.out.println("Gerado " + args[0] + " com " + pontos.size() + " locais: " + porTipo);
	}

	static String consultarOverpass() throws IOException, InterruptedException {
		String corpo = "data=" + URLEncoder.encode(CONSULTA, StandardCharsets.UTF_8);
		try (HttpClient cliente = HttpClient.newHttpClient()) {
			for (String servidor : SERVIDORES_OVERPASS) {
				HttpRequest pedido = HttpRequest.newBuilder(URI.create(servidor))
					.timeout(Duration.ofMinutes(2))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("User-Agent", "VozViva/0.1 (FIAP Global Solution; importacao unica)")
					.POST(HttpRequest.BodyPublishers.ofString(corpo))
					.build();
				try {
					HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
					if (resposta.statusCode() == 200 && resposta.body().startsWith("{")) {
						System.out.println("Dados obtidos de " + servidor);
						return resposta.body();
					}
					System.out.println(servidor + " respondeu HTTP " + resposta.statusCode() + "; tentando o próximo.");
				}
				catch (IOException ex) {
					System.out.println(servidor + " indisponível (" + ex.getClass().getSimpleName() + "); tentando o próximo.");
				}
			}
		}
		throw new IOException("Nenhum servidor Overpass respondeu. Tente mais tarde ou use -Pentrada=resposta.json.");
	}

	static List<Ponto> converter(JsonNode resposta) {
		List<Ponto> pontos = new ArrayList<>();
		for (JsonNode elemento : resposta.path("elements")) {
			Map<String, String> tags = new HashMap<>();
			elemento.path("tags").properties().forEach(e -> tags.put(e.getKey(), e.getValue().asString()));
			Optional<TipoAtendimento> tipo = ClassificadorOsm.tipo(tags);
			JsonNode posicao = elemento.has("lat") ? elemento : elemento.path("center");
			if (tipo.isEmpty() || !posicao.has("lat") || !posicao.has("lon")) {
				continue;
			}
			pontos.add(new Ponto(elemento.path("type").asString() + "/" + elemento.path("id").asLong(),
					limitar(tags.get("name").trim(), 200), tipo.get(),
					limitar(ClassificadorOsm.endereco(tags), 300), limitar(ClassificadorOsm.telefone(tags), 40),
					posicao.path("lat").asDouble(), posicao.path("lon").asDouble(),
					ClassificadorOsm.acessibilidade(tags), ClassificadorOsm.banheiroAdaptado(tags),
					ClassificadorOsm.funciona24h(tags)));
		}
		return removerDuplicatas(pontos);
	}

	/**
	 * Mesmo tipo e nome a menos de 200 m é o mesmo local: fica o registro com mais informação
	 * (acessibilidade conhecida, telefone, endereço).
	 */
	static List<Ponto> removerDuplicatas(List<Ponto> pontos) {
		List<Ponto> ordenados = new ArrayList<>(pontos);
		ordenados.sort(Comparator.comparingInt(Ponto::informacao).reversed().thenComparing(Ponto::osmId));
		List<Ponto> unicos = new ArrayList<>();
		for (Ponto ponto : ordenados) {
			boolean repetido = unicos.stream().anyMatch(u -> u.tipo() == ponto.tipo()
					&& ClassificadorOsm.normalizar(u.nome()).equals(ClassificadorOsm.normalizar(ponto.nome()))
					&& distancia(u, ponto) < DISTANCIA_DUPLICATA_METROS);
			if (!repetido) {
				unicos.add(ponto);
			}
		}
		unicos.sort(Comparator.comparing(Ponto::osmId));
		return unicos;
	}

	/**
	 * Gera a carga como atualização por {@code osm_id} (MERGE): um local que continua no mapa mantém o
	 * mesmo {@code id}, então links e favoritos salvos pelo app não quebram quando a carga é refeita.
	 * Só os locais que saíram do mapa são removidos.
	 */
	static String gerarSql(List<Ponto> pontos, Instant dataBase) {
		if (pontos.isEmpty()) {
			throw new IllegalArgumentException("Nenhum local na importação: a carga atual não será substituída.");
		}
		String data = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(dataBase);
		StringBuilder sql = new StringBuilder()
			.append("-- SPEC-007: carga dos locais de atendimento de Osasco/SP e arredores.\n")
			.append("-- Gerado por ./gradlew importarOsm a partir da Overpass API. Não edite à mão.\n")
			.append("-- Dados © colaboradores do OpenStreetMap, licença ODbL (https://www.openstreetmap.org/copyright).\n")
			.append("-- Base OSM de ").append(data).append(" UTC; ").append(pontos.size()).append(" locais.\n")
			.append("-- Migração repetível: o Flyway reaplica quando o conteúdo muda. A atualização é por osm_id,\n")
			.append("-- então os locais que continuam no mapa mantêm o mesmo id.\n")
			.append("DELETE FROM pontos_atendimento WHERE osm_id NOT IN (")
			.append(String.join(", ", pontos.stream().map(p -> texto(p.osmId())).toList()))
			.append(");\n");
		for (int inicio = 0; inicio < pontos.size(); inicio += LINHAS_POR_INSERT) {
			sql.append("\nMERGE INTO pontos_atendimento AS destino\nUSING (VALUES\n");
			List<Ponto> lote = pontos.subList(inicio, Math.min(inicio + LINHAS_POR_INSERT, pontos.size()));
			for (int i = 0; i < lote.size(); i++) {
				Ponto p = lote.get(i);
				sql.append("(").append(texto(p.osmId())).append(", ").append(texto(p.nome())).append(", ")
					.append(texto(p.tipo().name())).append(", ").append(texto(p.endereco())).append(", ")
					.append(texto(p.telefone())).append(", ").append(coordenada(p.latitude())).append(", ")
					.append(coordenada(p.longitude())).append(", ").append(texto(p.acessibilidade().name())).append(", ")
					.append(bit(p.banheiroAdaptado())).append(", ").append(bit(p.funciona24h())).append(", ")
					.append(texto(FONTE)).append(", '").append(data).append("')")
					.append(i < lote.size() - 1 ? ",\n" : "\n");
			}
			sql.append(") AS origem (").append(COLUNAS).append(")\n")
				.append("ON destino.osm_id = origem.osm_id\n")
				.append("WHEN MATCHED THEN UPDATE SET ")
				.append(String.join(", ", COLUNAS_ATUALIZADAS.stream().map(c -> c + " = origem." + c).toList()))
				.append("\nWHEN NOT MATCHED THEN INSERT (").append(COLUNAS).append(") VALUES (")
				.append(String.join(", ", TODAS_AS_COLUNAS.stream().map(c -> "origem." + c).toList()))
				.append(");\n");
		}
		return sql.toString();
	}

	private static Instant dataDosDados(JsonNode resposta) {
		String base = resposta.path("osm3s").path("timestamp_osm_base").asString("");
		return base.isBlank() ? Instant.now() : Instant.parse(base);
	}

	private static String texto(String valor) {
		return valor == null ? "NULL" : "N'" + valor.replace("'", "''") + "'";
	}

	private static String coordenada(double valor) {
		return String.format(Locale.ROOT, "%.6f", valor);
	}

	private static String bit(Boolean valor) {
		return valor == null ? "NULL" : valor ? "1" : "0";
	}

	private static String limitar(String valor, int maximo) {
		return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
	}

	private static double distancia(Ponto a, Ponto b) {
		double dLat = Math.toRadians(b.latitude() - a.latitude());
		double dLon = Math.toRadians(b.longitude() - a.longitude()) * Math.cos(Math.toRadians(a.latitude()));
		return 6_371_008.8 * Math.sqrt(dLat * dLat + dLon * dLon);
	}

	record Ponto(String osmId, String nome, TipoAtendimento tipo, String endereco, String telefone, double latitude,
			double longitude, Acessibilidade acessibilidade, Boolean banheiroAdaptado, Boolean funciona24h) {

		int informacao() {
			return (acessibilidade != Acessibilidade.DESCONHECIDA ? 4 : 0) + (telefone != null ? 2 : 0)
					+ (endereco != null ? 1 : 0);
		}

	}

}
