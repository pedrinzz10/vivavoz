# VozViva

Motor de inteligência e interface de voz do ecossistema **Fala Cidadão** (CitizenTech – FIAP Global Solution).
Microsserviço Java que ajuda idosos e pessoas com deficiência no dia a dia, por toque ou por voz: pedido de socorro
com aviso à rede de apoio, assistente contra golpes e atendimento acessível mais próximo com rota no celular.

As especificações das funcionalidades estão em [`specs/specs.json`](specs/specs.json).

## Stack

- Java 21 · Spring Boot 4 · Gradle
- Spring Data JPA + Flyway – Azure SQL em produção, SQL Server no desenvolvimento, H2 nos testes
- Spring Security – JWT e controle de acesso por papel
- Spring AI – assistente contra golpes (RAG) e classificação de intenção por voz
- RabbitMQ – evento assíncrono do botão de emergência

## Pré-requisitos

- JDK 21
- Docker (Docker Desktop no Windows)
- [Gitleaks](https://github.com/gitleaks/gitleaks) – `winget install Gitleaks.Gitleaks`

## Executando localmente

```bash
# 1. Variáveis de ambiente (o perfil dev lê o .env automaticamente)
cp .env.example .env        # preencha DB_PASSWORD, RABBITMQ_PASSWORD, JWT_SECRET, USUARIOS_TESTE_SENHA e OCI_GENAI_API_KEY

# 2. Infraestrutura (SQL Server + RabbitMQ); o banco "vozviva" é criado na primeira subida
docker compose up -d --wait

# 3. Aplicação (perfil dev por padrão)
./gradlew bootRun
```

- API: http://localhost:8080
- Health check: http://localhost:8080/actuator/health
- Painel do RabbitMQ: http://localhost:15672 (usuário `vozviva` e a senha definida em `RABBITMQ_PASSWORD`)

Para parar a infraestrutura: `docker compose down` (adicione `-v` para apagar os dados).

## Perfis

| Perfil | Uso | Conexões |
|---|---|---|
| `dev` (padrão) | Desenvolvimento local | Host, porta e usuário padrão compatíveis com o `docker-compose.yml`; senha vem do `.env` |
| `test` | Testes automatizados | H2 em memória (modo SQL Server); Testcontainers nos testes de integração; nenhum serviço externo necessário |
| `prod` | Produção | Sem valores padrão: falha ao iniciar se faltar `DB_URL`, as credenciais do banco ou do RabbitMQ |

Selecione com `SPRING_PROFILES_ACTIVE=prod`.

## Variáveis de ambiente

| Variável | Descrição |
|---|---|
| `OCI_GENAI_API_KEY` | API key do OCI Generative AI (sem ela, o assistente usa só as regras) |
| `OCI_GENAI_BASE_URL` | Endpoint compatível com OpenAI da OCI (padrão `https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/openai/v1`) |
| `OCI_GENAI_MODEL` | Modelo usado (padrão `openai.gpt-oss-120b`) |
| `VOZ_CONFIANCA_MINIMA` | Confiança mínima para a IA decidir a intenção sozinha (padrão `0.6`) |
| `DB_URL` | URL JDBC do banco (Azure SQL em produção; padrão do dev aponta para o SQL Server do docker-compose) |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciais do banco (no dev, usuário `sa` e a senha do `.env`) |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | Endereço do RabbitMQ |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | Credenciais do RabbitMQ |
| `JWT_SECRET` | Segredo de assinatura do JWT, compartilhado com a API .NET (mínimo de 32 caracteres). Obrigatório, exceto se `JWT_JWKS_URI` for usado |
| `JWT_ISSUER` | Emissor esperado no token (claim `iss`). Recomendado |
| `JWT_AUDIENCE` | Audiência esperada no token (claim `aud`). Opcional |
| `JWT_ALGORITMO` | `HS256` (padrão), `HS384` ou `HS512` |
| `JWT_EXPIRACAO` | Validade do token emitido no login (padrão `2h`) |
| `USUARIOS_TESTE_HABILITADO` | Cria os usuários de teste ao iniciar (padrão `true` no dev e `false` no prod) |
| `USUARIOS_TESTE_SENHA` | Senha dos usuários de teste; sem ela, nada é criado |

## Padrão DTO

Os controllers nunca expõem entidades, `Map` ou `Object`: tudo que entra e sai da API é um **DTO**.

| Regra | Exemplo |
|---|---|
| DTOs ficam no subpacote `dto` do módulo | `perfil.dto.ContatoEmergenciaRequest` |
| DTOs são `record` (imutáveis) | `public record ContatoEmergenciaResponse(Long id, String nome) {}` |
| Entrada termina em `Request`, saída em `Response` | `ContatoEmergenciaRequest`, `ContatoEmergenciaResponse` |
| Endpoints recebem DTO no `@RequestBody` e devolvem DTO (ou `ResponseEntity`/`List` de DTO) | `ResponseEntity<ContatoEmergenciaResponse> criar(@RequestBody @Valid ContatoEmergenciaRequest r)` |
| Controllers não acessam entidades (`@Entity`) nem repositórios: passam por um serviço | `controller → service → repository` |
| DTOs não dependem de entidades; a conversão fica em um mapper do módulo | `ContatoEmergenciaMapper.paraResponse(contato)` |
| Validação (`@NotBlank`, `@Valid`...) fica no DTO de entrada | `record ContatoEmergenciaRequest(@NotBlank String nome, ...)` |

Essas regras são verificadas por testes de arquitetura com ArchUnit (`ArquiteturaDtoTest`): uma
violação quebra o build. O contrato de erro também é um DTO: `comum.dto.ErroResponse`.

## Segurança

A API Java é a dona da identidade do ecossistema: cadastra os usuários e emite o JWT. Ela valida o token
em todas as chamadas (Resource Server) e aplica controle de acesso por papel. A API .NET valida o mesmo
token, com o mesmo segredo e emissor.

| Endpoint | Acesso | O que faz |
|---|---|---|
| `POST /api/v1/auth/cadastro` | Público | Cria a conta (`nome`, `email`, `senha`, `papel`: `CIDADAO` ou `CUIDADOR`) |
| `POST /api/v1/auth/login` | Público | Devolve `accessToken`, `tokenType`, `expiraEm` e os dados do usuário |
| `GET /api/v1/auth/eu` | Token | Dados do usuário autenticado |

Use o token em todas as outras chamadas:

```
Authorization: Bearer <accessToken>
```

**Usuários de teste** (criados no perfil dev, ou em produção com `USUARIOS_TESTE_HABILITADO=true`), com a
senha definida em `USUARIOS_TESTE_SENHA`:

| E-mail | Papel |
|---|---|
| `cidadao.teste@vozviva.dev` | `CIDADAO` |
| `cuidador.teste@vozviva.dev` | `CUIDADOR` |

Proteções do login: senha com BCrypt (mínimo de 8 caracteres, com letra e número); a mesma resposta para
e-mail inexistente e senha errada, inclusive no tempo de resposta; bloqueio de 15 minutos (HTTP 429) depois
de 5 erros seguidos no mesmo e-mail.

### Contrato do token (para a API .NET validar)

| Item | Valor |
|---|---|
| Algoritmo | HS256, com o mesmo `JWT_SECRET` (mínimo de 32 caracteres) |
| `iss` | Valor de `JWT_ISSUER` |
| `sub` | Id do usuário |
| `role` | `CIDADAO` ou `CUIDADOR` |
| `name` | Nome do usuário |
| `iat` / `exp` | Emissão e expiração (validade de `JWT_EXPIRACAO`) |

Na validação, o token precisa ter assinatura válida, `exp` (tokens sem expiração são recusados), o `iss`
configurado e um papel conhecido; valores desconhecidos nunca dão acesso.

### Matriz de acesso

| Rota | Acesso |
|---|---|
| `GET /actuator/health`, `GET /actuator/info` | Público |
| `POST /api/v1/auth/cadastro`, `POST /api/v1/auth/login` | Público |
| `GET /v3/api-docs/**`, `GET /swagger-ui/**` | Público |
| Consumo de dados de suporte (atendimentos próximos, verificação de golpes, classificação por voz...) | `CIDADAO` ou `CUIDADOR` |
| Alteração de perfil de emergência | `CUIDADOR` (vinculado ao cidadão) |
| Qualquer outra rota | Token válido |

As regras por papel ficam nos endpoints com `@PreAuthorize`. Respostas 401 e 403 seguem o formato de
erro padrão da API, com `mensagemFalada` e `traceId`.

### CSRF

A proteção CSRF continua **ativa**, mas requisições com header `Authorization` não precisam do token CSRF:
um site malicioso não consegue fazer o navegador enviar esse header, e por isso o ataque não se aplica.
Assim, o app envia apenas o JWT, sem requisição extra para buscar um token CSRF. Requisições sem
`Authorization` continuam passando pelo CSRF (token em cookie `HttpOnly`, `Secure`, `SameSite=Strict`),
o que já protege um eventual login por cookie no futuro. Uma escrita sem JWT recebe 401
(`NAO_AUTENTICADO`), para o app saber que precisa entrar de novo.

## Perfil de emergência e contatos

O cuidador mantém pelo app o perfil de emergência do cidadão (mobilidade, condições de saúde, medicamentos) e
os contatos avisados quando ele aciona o socorro.

| Endpoint | Acesso | O que faz |
|---|---|---|
| `POST /api/v1/perfis-emergencia` | `CUIDADOR` | Cria o perfil informando o e-mail da conta do cidadão (`cidadaoEmail`) |
| `GET /api/v1/perfis-emergencia` | `CUIDADOR` | Lista paginada dos perfis que ele gerencia |
| `GET /api/v1/perfis-emergencia/meu` | `CIDADAO` | O perfil do próprio cidadão |
| `GET /api/v1/perfis-emergencia/{id}` | Cuidador responsável ou o cidadão | Detalhe do perfil |
| `PUT` / `DELETE /api/v1/perfis-emergencia/{id}` | Cuidador responsável | Atualiza / exclui |
| `POST /api/v1/perfis-emergencia/{id}/contatos` | Cuidador responsável | Adiciona contato (até 10) |
| `GET /api/v1/perfis-emergencia/{id}/contatos` | Cuidador responsável ou o cidadão | Lista paginada dos contatos |
| `GET` / `PUT` / `DELETE /api/v1/perfis-emergencia/{id}/contatos/{contatoId}` | Leitura: ambos; alteração: cuidador | Detalhe / atualiza / exclui |

**Paginação:** `?page=0&size=10&sort=nome,asc` (a primeira página é a 0; no máximo 50 itens). A ordenação só
aceita campos públicos (perfis: `id`, `mobilidade`, `criadoEm`, `atualizadoEm`; contatos: `id`, `nome`,
`prioridade`, `parentesco`). Telefones aceitam os formatos brasileiros comuns e são devolvidos no padrão
`+55DDDNUMERO`, pronto para o app ligar.

**Limitação conhecida:** o cuidador vincula o perfil pelo e-mail da conta do cidadão, sem confirmação do
cidadão. Num sistema em produção, o vínculo seria confirmado por convite.
## Botão de emergência (arquitetura distribuída — opção A: RabbitMQ)

```
App ──POST /api/v1/emergencias──▶ grava Emergencia (ACIONADA) ──após o commit──▶ publica EmergenciaAcionadaEvento
    ◀── 202 Accepted na hora                                                          │
                                                                                      ▼
               exchange vozviva.emergencias ──(emergencia.acionada)──▶ fila vozviva.emergencias.acionadas
                                                                                      │
          ConsumidorEmergencia: avisa os contatos por prioridade, grava cada aviso, status NOTIFICADA
                     3 tentativas com espera crescente ──falhou──▶ DLQ vozviva.emergencias.dlq
```

| Endpoint | Acesso | O que faz |
|---|---|---|
| `POST /api/v1/emergencias` | `CIDADAO` | Aciona a emergência (`latitude`, `longitude` e `textoOriginal` opcionais) → **202** |
| `GET /api/v1/emergencias` | `CIDADAO` / `CUIDADOR` | Histórico paginado (do cidadão ou dos perfis do cuidador) |
| `GET /api/v1/emergencias/{id}` | Cidadão dono ou cuidador responsável | Status atual |
| `GET /api/v1/emergencias/{id}/avisos` | Cidadão dono ou cuidador responsável | Quem foi avisado e com qual mensagem |
| `PATCH /api/v1/emergencias/{id}/cancelar` | `CIDADAO` dono | Cancela um acionamento por engano |
| `PATCH /api/v1/emergencias/{id}/atender` | `CUIDADOR` responsável | Encerra como atendida |

Garantias:
- **Publicação só depois do commit:** o consumidor nunca procura uma emergência que ainda não foi gravada.
- **Broker fora do ar não vira erro para o cidadão:** a emergência fica `ACIONADA` e o `ReenvioEmergencias`
  publica de novo a cada 30 s.
- **Consumo idempotente com trava da linha:** mensagens repetidas (reenvio ou nova entrega do broker) nunca
  avisam os contatos em dobro.
- **Toque duplo:** um novo acionamento em até 2 minutos devolve a emergência que já está aberta.
- **DLQ:** depois de 3 tentativas, a mensagem vai para `vozviva.emergencias.dlq` para análise.
- O envio real (SMS/WhatsApp) fica atrás da interface `NotificadorContatos`; a implementação padrão registra o
  aviso no log com o telefone mascarado.

O painel do RabbitMQ (http://localhost:15672) mostra as filas e as mensagens em JSON.
## Assistente contra golpes (Spring AI + RAG)

O cidadão cola ou dita uma mensagem suspeita e recebe, em linguagem simples, se parece golpe e o que fazer.

```
mensagem ──▶ 1. Regras determinísticas (senha, Pix com pressa, link, "troquei de número"...)  ─┐
         ──▶ 2. Recuperação (RAG): orientações oficiais mais parecidas, por TF-IDF              │
         ──▶ 3. IA generativa (Spring AI): analisa a mensagem com as orientações e devolve       │
                JSON estruturado (classificação, tipo, sinais, orientação, canal, frase falada)  │
                                                                                                ▼
                                 resposta = maior risco entre IA e regras  +  fontes oficiais usadas
```

| Endpoint | Acesso | O que faz |
|---|---|---|
| `POST /api/v1/verificacoes` | `CIDADAO` / `CUIDADOR` | Analisa `{ "texto": "...", "canal": "WHATSAPP" }` |
| `GET /api/v1/verificacoes/estatisticas?dias=30` | `CIDADAO` / `CUIDADOR` | Golpes mais frequentes no período (anonimizado) |

- **Base de conhecimento:** `src/main/resources/conhecimento/golpes.json`, com 14 orientações curadas, cada
  uma com o órgão de origem (INSS, Febraban, Banco Central, Procon-SP, Polícia Civil-SP, Ministério da Saúde,
  Receita Federal, Correios, MDHC) e o canal oficial para confirmar.
- **Por que TF-IDF e não busca vetorial:** a base é pequena e curada, a busca fica determinística e testável e
  não depende de um modelo de embeddings (o endpoint compatível com OpenAI da OCI não expõe embeddings). A evolução natural é a busca vetorial
  (embeddings da OCI ou Oracle 23ai Vector Search).
- **Segurança da IA:** a mensagem recebida vai delimitada e é tratada pelo modelo como dado, não como
  instrução (proteção contra *prompt injection*); as regras garantem que um pedido de senha nunca sai como
  "sem risco", mesmo que o modelo erre; sem IA (fora do ar, sem chave ou timeout de 30 s), as regras
  respondem sozinhas.
- **LGPD:** o texto da mensagem e o usuário nunca são gravados; só tipo, classificação, canal e data.
- **Modelo:** OCI Generative AI pelo endpoint compatível com a API da OpenAI (Spring AI + SDK oficial da
  OpenAI), configurável por `OCI_GENAI_MODEL` (padrão `openai.gpt-oss-120b`). O código depende só do
  `ChatModel` do Spring AI, então trocar de modelo ou provedor é só configuração.

### Avaliação

Conjunto versionado de 30 mensagens rotuladas (18 golpes, 12 legítimas) em
`src/test/resources/avaliacao/mensagens-rotuladas.json`, com golpes sem palavras óbvias e mensagens legítimas
de banco e saúde. "Golpe detectado" = `PROVAVEL_GOLPE` ou `SUSPEITA`.

| Configuração | Acurácia | Recall | Precisão | Falsos alarmes | Como rodar |
|---|---|---|---|---|---|
| Só regras (linha de base) | 83,3% | 72,2% | 100% | 0 | `./gradlew test` (relatório em `build/reports/avaliacao-golpes/somente-regras.md`) |
| IA + regras | meta ≥ 80% | meta ≥ 90% | — | — | `./gradlew avaliarIa` (exige `OCI_GENAI_API_KEY`; consome créditos da OCI) |

Os golpes que as regras não pegam são os semânticos (falso advogado, motoboy, "vaga de emprego"), que é
onde a IA agrega. As regras foram ajustadas com padrões gerais conhecidos ("somente hoje", "perdi meu
celular", "dados bancários"), não com as frases do conjunto; por ser pequeno, ele indica tendência, não
garante desempenho.

**Limitações:** o modelo pode errar golpes novos ou muito bem escritos (por isso a resposta nunca afirma
certeza e sempre indica o canal oficial); a base cobre os golpes mais comuns, não todos; a classificação não
substitui a confirmação no canal oficial.
## Classificação de intenção por voz (Spring AI + RAG)

O app transcreve a fala do cidadão e envia o texto. A API devolve qual tela abrir, com os dados já extraídos.
Ela só classifica e orienta: não executa nenhuma ação, e o socorro, por exemplo, é confirmado pela pessoa na tela.

| Rota | Papel | O que faz |
|---|---|---|
| `POST /api/v1/voz/intencoes` | `CIDADAO` / `CUIDADOR` | `{ "texto": "...", "localizacao": { "latitude": -23.53, "longitude": -46.78 } }` (localização opcional) |
| `GET /api/v1/voz/interacoes?page=0&size=10` | `CIDADAO` / `CUIDADOR` | Histórico do próprio usuário, das mais recentes para as mais antigas |

| Intenção | Ação do app | Exemplo de fala |
|---|---|---|
| `SOCORRO` | `ACIONAR_SOCORRO` | "Caí e não consigo levantar" |
| `BUSCAR_ATENDIMENTO` | `MOSTRAR_ATENDIMENTOS` | "Onde tem farmácia aqui perto?" |
| `VERIFICAR_INFORMACAO` | `MOSTRAR_VERIFICACAO` | "Recebi uma mensagem do INSS, é golpe?" |
| `ZELADORIA_REPORT` | `ABRIR_ZELADORIA_DOTNET` | "Tem um buraco na calçada e não passo com a cadeira" |
| `DESCONHECIDA` | `PEDIR_REPETICAO` | "Bom dia" |

A resposta traz `intencao`, `acao`, `urgencia` (`BAIXA` a `CRITICA`), `entidades` (`tipoAtendimento`,
`exigeAcessibilidade`, `problemaRua`, `impedeCadeirante`, `referenciaLocal`), `mensagemFalada` (até 200
caracteres), `confianca` e `analisadoPor`. Em `BUSCAR_ATENDIMENTO` com localização, ela já traz os
atendimentos mais próximos (o mesmo resultado de `GET /api/v1/atendimentos/proximos`): um toque a menos.

- **Saída estruturada:** o Spring AI mapeia a resposta do modelo direto para um record (`entity()`). A ação
  não vem do modelo: ela é decidida no servidor a partir da intenção, então a IA nunca dispara uma ação
  incoerente.
- **RAG:** os exemplos curados mais parecidos com a fala (`conhecimento/exemplos-intencoes.json`, 29 falas,
  recuperadas com o mesmo TF-IDF do assistente contra golpes) vão no prompt.
- **Segurança:**
  - Um pedido de socorro reconhecido pelas regras ou pelo modelo sempre vira `SOCORRO`, mesmo com pouca
    confiança. Na dúvida o app oferece ajuda, e a pessoa confirma na tela.
  - A fala vai delimitada e é tratada pelo modelo como dado (proteção contra *prompt injection*).
- **Confiança:** abaixo de `VOZ_CONFIANCA_MINIMA` (padrão 0,6), a IA não decide sozinha: valem as regras ou o
  app pede para repetir ("Você quer pedir ajuda, achar um atendimento perto, conferir uma mensagem ou avisar
  de um problema na rua?").
- **Sem IA:** as regras determinísticas respondem, sempre no mesmo contrato JSON. Isso vale com o modelo fora
  do ar, sem chave ou com resposta malformada.
- **LGPD:** o histórico guarda o texto, a intenção, a confiança e a latência, para o próprio usuário consultar
  e para avaliar a IA. A localização nunca é gravada.

### Avaliação

Conjunto versionado de 32 falas rotuladas (`src/test/resources/avaliacao/frases-intencoes.json`), diferentes
dos exemplos do RAG. Inclui transcrições sem acento e armadilhas, como "pronto socorro" (um lugar, não um pedido
de socorro) e "a árvore caiu" (zeladoria, não queda de uma pessoa).

| Configuração | Acurácia | Socorros reconhecidos | Socorros falsos | Como rodar |
|---|---|---|---|---|
| Só regras (linha de base) | 96,9% | 100% | 0 | `./gradlew test` (relatório em `build/reports/avaliacao-intencoes/`) |
| IA + regras | meta ≥ 90% | meta 100% | — | `./gradlew avaliarIa` (exige `OCI_GENAI_API_KEY`; consome créditos da OCI) |

Duas falhas das regras foram corrigidas depois da primeira medição: "alagou" não casava com "alaga", e
"calçada está toda quebrada" tinha palavras no meio. As duas correções são de padrão, e não de frase do
conjunto; mesmo assim, a linha de base fica otimista. A falha que sobrou ("meu remédio acabou, onde eu compro")
é semântica, que é onde a IA agrega. Com 32 falas, o resultado indica tendência, não garante desempenho.

## Atendimento acessível mais próximo ("Ir até lá")

Com um toque ("Hospital mais perto") ou por voz, o idoso ou a PcD encontra o atendimento do tipo desejado mais
perto de onde está, de preferência acessível, e abre a rota no Google Maps, no Waze ou em qualquer app que
entenda `geo:`. Não usa API paga de mapas: os links de rota não exigem chave.

| Rota | Papel | O que faz |
|---|---|---|
| `GET /api/v1/atendimentos/proximos?tipo=HOSPITAL&latitude=-23.53&longitude=-46.78&acessivel=true` | `CIDADAO` / `CUIDADOR` | Mais próximos no raio (`raioMetros`, padrão 5 km, até 20 km), até `limite` itens (padrão 3, máximo 10) |
| `GET /api/v1/atendimentos?tipo=UBS&page=0&size=10&sort=nome` | `CIDADAO` / `CUIDADOR` | Lista paginada (ordenação por `nome` ou `tipo`) |
| `GET /api/v1/atendimentos/{id}` | `CIDADAO` / `CUIDADOR` | Detalhe do local |

Tipos: `HOSPITAL`, `UPA`, `UBS`, `FARMACIA`, `CRAS`, `DELEGACIA`, `DEFENSORIA`.

- **Busca:** pré-filtro por área no banco (índice em tipo + latitude + longitude) e distância exata por
  Haversine, do mais próximo para o mais distante. A base inteira tem poucas centenas de linhas, então a
  resposta fica bem abaixo de 1 s.
- **Acessibilidade primeiro:** com `acessivel=true`, só entram locais com acessibilidade `SIM` ou `PARCIAL`,
  mesmo que estejam mais longe. Se não houver nenhum no raio, a resposta traz os de acessibilidade
  `DESCONHECIDA` com `semAcessivelNoRaio=true`, um aviso em cada item e a orientação de ligar antes. Um local
  sem informação nunca é apresentado como acessível.
- **Cada item** traz distância, acessibilidade, telefone, banheiro adaptado, 24 h e os links prontos
  (`rotas.googleMaps`, `rotas.waze`, `rotas.geo`), além de uma `mensagemFalada` curta
  ("O hospital mais perto com acessibilidade fica a 1,2 km. Se for urgência, ligue 192. Quer que eu abra o caminho?").
- **Urgência:** buscas por `HOSPITAL` e `UPA` sempre trazem `telefoneEmergencia: "192"` e a orientação de
  ligar para o SAMU em vez de ir sozinho.
- **LGPD:** a localização do usuário só é usada no cálculo e nunca é gravada.

### Dados (OpenStreetMap)

Os locais vêm de uma importação única de dados abertos do **OpenStreetMap** para Osasco/SP e arredores
(bairros vizinhos de São Paulo, Carapicuíba e Barueri):

```bash
./scripts/importar-osm.sh                                 # consulta a Overpass API (com espelhos de reserva)
./scripts/importar-osm.sh -Pentrada=resposta-overpass.json  # a partir de uma resposta salva
```

O importador (`atendimento.importacao`) classifica cada elemento pelo nome e pelas tags (ex.: "UPA" ou
"Pronto Atendimento" vira `UPA`; "UBS" ou "Unidade Básica" vira `UBS`; só delegacias e distritos policiais
entram como `DELEGACIA`), converte a tag `wheelchair` (`yes`/`limited`/`no`) em `SIM`/`PARCIAL`/`NAO`, junta
o mesmo local mapeado duas vezes e gera `R__carga_pontos_atendimento.sql`, uma migração repetível do Flyway
(reaplicada quando o conteúdo muda).

Carga atual (base OSM de 28/07/2026): 237 locais, sendo 130 farmácias, 40 hospitais, 38 UBS, 25 delegacias
e 4 UPAs. Só 28 têm a acessibilidade informada no OSM; os demais aparecem como "acessibilidade não
informada".

**Dados © colaboradores do OpenStreetMap**, sob a licença [ODbL](https://www.openstreetmap.org/copyright).
O crédito também vai em toda resposta da busca (`creditoDados`), para o app exibir.

**Limitações:**
- O destino é filtrado pela acessibilidade, mas a rota calculada pelo app de navegação pode passar por
  calçadas ou travessias sem acessibilidade. A resposta avisa isso em `observacao`.
- A qualidade depende do mapeamento colaborativo: muitos locais não têm acessibilidade nem telefone
  informados.
- CRAS e Defensoria Pública ainda não estão mapeados no OSM na região. Os tipos existem e passam a aparecer
  quando forem mapeados e a carga for refeita.

## Banco de dados

O esquema é versionado com **Flyway** em `src/main/resources/db/migration` (`V1__descricao.sql`, `V2__...`).
A carga dos locais de atendimento é a migração repetível `R__carga_pontos_atendimento.sql`.
O Hibernate apenas valida as entidades (`ddl-auto=validate`); nunca cria tabelas sozinho.

`scripts/script-bd.sql` reúne todas as migrações (DDL e carga) num único script (exigência da disciplina de DevOps). Depois de
criar ou alterar uma migração, rode `./gradlew gerarScriptBd`: o `check` (e o CI) falha se o arquivo estiver
desatualizado.

## Testes

```bash
./gradlew test                    # unitários + integração
./gradlew test jacocoTestReport   # com relatório de cobertura em build/reports/jacoco
```

Os testes comuns usam H2 em memória (modo SQL Server). Os testes de integração sobem SQL Server (mesmo motor
do Azure SQL) e RabbitMQ reais com Testcontainers. Sem Docker, eles são ignorados automaticamente e os demais
testes continuam rodando.

Nos testes, o segredo JWT é gerado aleatoriamente a cada execução (`SegredoJwtDeTeste`), e
`TokenDeTeste` assina tokens no formato do .NET, como em `TokenDeTeste.cuidador("42").bearer()`.
Nenhum segredo fica no repositório.

## Estrutura de pacotes

```
br.com.fiap.vozviva
├── usuario      cadastro, login e emissão do JWT
├── golpe        assistente contra golpes (regras + RAG + Spring AI)
├── voz          classificação de intenção por voz (Spring AI)
├── perfil       perfil de emergência e contatos (cuidador)
├── emergencia   botão de emergência assíncrono (RabbitMQ: publicador, consumidor, DLQ e reenvio)
├── atendimento  atendimento acessível mais próximo e importador do OpenStreetMap
├── seguranca    JWT e controle de acesso
└── comum        erros, DTOs compartilhados e utilitários
```

Dentro de cada módulo: `dto` (records de entrada e saída), controller, serviço, mapper, entidade e
repositório.

## Fluxo de contribuição

A branch `main` é protegida: nenhum push direto é aceito.

1. Configure os hooks locais uma vez por clone: `git config core.hooksPath .githooks`
2. Crie uma branch: `git switch -c feat/spec-xxx-descricao`
3. Faça commits (o hook `pre-commit` bloqueia segredos e arquivos sensíveis)
4. Faça push (o hook `pre-push` roda os testes)
5. Abra um Pull Request para a `main`

O merge só é liberado quando os checks **Build e Testes** e **Varredura de Segredos** passam. O CodeQL e a revisão de
dependências também rodam em cada PR.

Nunca commite segredos: use variáveis de ambiente e o arquivo `.env` (já ignorado pelo Git).
