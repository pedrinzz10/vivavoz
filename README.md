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
cp .env.example .env        # preencha DB_PASSWORD, RABBITMQ_PASSWORD, JWT_SECRET, USUARIOS_TESTE_SENHA e ANTHROPIC_API_KEY

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
| `ANTHROPIC_API_KEY` | Chave da API Anthropic (Spring AI) |
| `ANTHROPIC_MODEL` | Modelo usado (padrão `claude-sonnet-5`) |
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
| Consumo de dados de suporte (atendimentos próximos, verificação de golpes...) | `CIDADAO` ou `CUIDADOR` |
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

## Banco de dados

O esquema é versionado com **Flyway** em `src/main/resources/db/migration` (`V1__descricao.sql`, `V2__...`).
O Hibernate apenas valida as entidades (`ddl-auto=validate`); nunca cria tabelas sozinho.

`scripts/script-bd.sql` reúne todas as migrações num único DDL (exigência da disciplina de DevOps). Depois de
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
├── voz          classificação de intenção por voz (Spring AI)
├── perfil       perfil de emergência e contatos (cuidador)
├── emergencia   botão de emergência assíncrono (RabbitMQ)
├── seguranca    JWT e controle de acesso
└── comum        erros, DTOs compartilhados e utilitários
```

Os módulos do assistente contra golpes e do atendimento mais próximo entram com as suas specs.

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
