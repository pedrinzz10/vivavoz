# VozViva

Motor de inteligência e interface de voz do ecossistema **Fala Cidadão** (CitizenTech – FIAP Global Solution).
Microsserviço Java que transforma a fala de idosos e pessoas com deficiência em ações concretas: zeladoria de calçadas,
pedido de socorro, alertas climáticos, saúde preventiva e compra direta da agricultura familiar.

As especificações das funcionalidades estão em [`specs/specs.json`](specs/specs.json).

## Stack

- Java 21 · Spring Boot 4 · Gradle
- Spring AI (Anthropic) – classificação de intenção
- MongoDB (GeoJSON / 2dsphere) – dados geoespaciais
- RabbitMQ – eventos de emergência e alertas
- OpenFeign – integração com a API .NET (Oracle)

## Pré-requisitos

- JDK 21
- Docker (Docker Desktop no Windows)
- [Gitleaks](https://github.com/gitleaks/gitleaks) – `winget install Gitleaks.Gitleaks`

## Executando localmente

```bash
# 1. Variáveis de ambiente (o perfil dev lê o .env automaticamente)
cp .env.example .env        # preencha RABBITMQ_PASSWORD, JWT_SECRET e ANTHROPIC_API_KEY

# 2. Infraestrutura (MongoDB + RabbitMQ)
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
| `test` | Testes automatizados | Testcontainers nos testes de integração; nenhum serviço externo necessário |
| `prod` | Produção | Sem valores padrão: falha ao iniciar se faltar `MONGODB_URI` ou as credenciais do RabbitMQ |

Selecione com `SPRING_PROFILES_ACTIVE=prod`.

## Variáveis de ambiente

| Variável | Descrição |
|---|---|
| `ANTHROPIC_API_KEY` | Chave da API Anthropic (Spring AI) |
| `ANTHROPIC_MODEL` | Modelo usado (padrão `claude-sonnet-5`) |
| `MONGODB_URI` | URI de conexão do MongoDB |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | Endereço do RabbitMQ |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | Credenciais do RabbitMQ |
| `JWT_SECRET` | Segredo compartilhado com a API .NET para validar o JWT (mínimo de 32 caracteres). Obrigatório, exceto se `JWT_JWKS_URI` for usado |
| `JWT_ISSUER` | Emissor esperado no token (claim `iss`). Recomendado |
| `JWT_AUDIENCE` | Audiência esperada no token (claim `aud`). Opcional |
| `JWT_ALGORITMO` | `HS256` (padrão), `HS384` ou `HS512` |
| `JWT_JWKS_URI` | Alternativa ao segredo: endpoint JWKS publicado pelo .NET |
| `DOTNET_API_URL` | URL da API .NET Core (a partir da SPEC-011) |
| `EUREKA_ENABLED` | Ativa o registro no Eureka (padrão `false`) |

## Padrão DTO

Os controllers nunca expõem entidades, `Map` ou `Object`: tudo que entra e sai da API é um **DTO**.

| Regra | Exemplo |
|---|---|
| DTOs ficam no subpacote `dto` do módulo | `zeladoria.dto.ObstrucaoRequest` |
| DTOs são `record` (imutáveis) | `public record ObstrucaoResponse(String id, String tipo) {}` |
| Entrada termina em `Request`, saída em `Response` | `ObstrucaoRequest`, `ObstrucaoResponse` |
| Endpoints recebem DTO no `@RequestBody` e devolvem DTO (ou `ResponseEntity`/`List` de DTO) | `ResponseEntity<ObstrucaoResponse> criar(@RequestBody @Valid ObstrucaoRequest r)` |
| Controllers não acessam entidades (`@Document`) nem repositórios: passam por um serviço | `controller → service → repository` |
| DTOs não dependem de entidades; a conversão fica em um mapper do módulo | `ObstrucaoMapper.paraResponse(obstrucao)` |
| Validação (`@NotBlank`, `@Valid`...) fica no DTO de entrada | `record ObstrucaoRequest(@NotBlank String tipo, ...)` |

Essas regras são verificadas por testes de arquitetura com ArchUnit (`ArquiteturaDtoTest`): uma
violação quebra o build. O contrato de erro também é um DTO: `comum.dto.ErroResponse`.

## Segurança

A API Java não emite tokens: ela valida o **JWT emitido pela API .NET** (Resource Server) e aplica
controle de acesso por papel. Envie o token em todas as chamadas:

```
Authorization: Bearer <token do login .NET>
```

O token precisa ter assinatura válida, `exp` (tokens sem expiração são recusados), o `iss` configurado
e um papel. Os papéis são lidos das claims `roles`, `role` ou da claim de papel padrão do ASP.NET, como
lista ou texto. Valores desconhecidos são ignorados. O identificador do usuário vem de `sub`, `nameid`
ou da claim `NameIdentifier` do ASP.NET.

### Matriz de acesso

| Rota | Acesso |
|---|---|
| `GET /actuator/health`, `GET /actuator/info` | Público |
| `GET /v3/api-docs/**`, `GET /swagger-ui/**` | Público |
| Consumo de dados de suporte (rede, alertas, agendamentos...) | `CIDADAO` ou `CUIDADOR` |
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

## Testes

```bash
./gradlew test                    # unitários + integração
./gradlew test jacocoTestReport   # com relatório de cobertura em build/reports/jacoco
```

Os testes de integração sobem MongoDB e RabbitMQ reais com Testcontainers. Sem Docker, eles são ignorados
automaticamente e os demais testes continuam rodando.

Nos testes, o segredo JWT é gerado aleatoriamente a cada execução (`SegredoJwtDeTeste`), e
`TokenDeTeste` assina tokens no formato do .NET, como em `TokenDeTeste.cuidador("42").bearer()`.
Nenhum segredo fica no repositório.

## Estrutura de pacotes

```
br.com.fiap.vozviva
├── voz          classificação de intenção (Spring AI + RAG)
├── zeladoria    obstruções de mobilidade
├── rede         abrigos, postos de saúde e voluntários
├── emergencia   botão de emergência assíncrono
├── clima        alertas climáticos e rotas de fuga
├── saude        agendamentos e tradução de termos técnicos
├── economia     agricultura familiar e pedidos
├── perfil       perfil de emergência (cuidador)
├── notificacao  notificações amigáveis
├── dados        relatórios anonimizados
├── integracao   clientes da API .NET
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
