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
cp .env.example .env        # preencha ANTHROPIC_API_KEY

# 2. Infraestrutura (MongoDB + RabbitMQ)
docker compose up -d --wait

# 3. Aplicação (perfil dev por padrão)
./gradlew bootRun
```

- API: http://localhost:8080
- Health check: http://localhost:8080/actuator/health
- Painel do RabbitMQ: http://localhost:15672 (usuário e senha definidos no `.env`, padrão `vozviva` / `vozviva`)

Para parar a infraestrutura: `docker compose down` (adicione `-v` para apagar os dados).

## Perfis

| Perfil | Uso | Conexões |
|---|---|---|
| `dev` (padrão) | Desenvolvimento local | Valores padrão compatíveis com o `docker-compose.yml`; lê o `.env` |
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
| `DOTNET_API_URL` | URL da API .NET Core (a partir da SPEC-011) |
| `JWT_ISSUER` / `JWT_SECRET` | Validação do JWT emitido pelo .NET (a partir da SPEC-002) |
| `EUREKA_ENABLED` | Ativa o registro no Eureka (padrão `false`) |

## Testes

```bash
./gradlew test                    # unitários + integração
./gradlew test jacocoTestReport   # com relatório de cobertura em build/reports/jacoco
```

Os testes de integração sobem MongoDB e RabbitMQ reais com Testcontainers. Sem Docker, eles são ignorados
automaticamente e os demais testes continuam rodando.

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
└── comum        erros, DTOs base e utilitários
```

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
