# Roteiro de estudo para as apresentações

Guia de revisão para as bancas de **Java Advanced**, **IA generativa**, **DevOps** e **Compliance/QA**, que usam
este repositório. Cada bloco traz o que mostrar, as perguntas prováveis e uma resposta curta, com o arquivo onde
está o código para abrir na hora.

## Antes de entrar (checklist)

- [ ] API no ar: `https://<webAppName>.azurewebsites.net/actuator/health` responde `UP`.
- [ ] Swagger publicado aberto, com o token de um usuário de teste já em **Authorize**.
- [ ] Contas `cidadao.teste@` e `cuidador.teste@` criadas (`USUARIOS_TESTE_SENHA` no provisionamento).
- [ ] Query editor do Azure SQL aberto, para mostrar os `SELECT` depois de cada CRUD.
- [ ] Azure DevOps aberto em Boards, Pipelines (último build verde), Releases e Test Plans.
- [ ] Credencial da OCI configurada (`OCI_GENAI_API_KEY` ou as variáveis `OCI_GENAI_*` da assinatura), senão a IA cai nas regras (funciona, mas não mostra o LLM).
- [ ] App mobile instalado apontando para a URL da Azure.

## Roteiro da demonstração (ponta a ponta, uns 4 minutos)

1. **Login** no app como cidadão. No Swagger, mostrar o token e o `GET /api/v1/auth/eu`.
2. **Perfil e contatos:** o cidadão cria o próprio perfil e um contato. Mostrar o `SELECT` em
   `contatos_emergencia` com o telefone gravado como `+55DDDNUMERO`.
3. **Voz:** dizer "caí e não consigo levantar". A resposta traz `SOCORRO` e `ACIONAR_SOCORRO`, e o app abre o
   socorro.
4. **Botão de emergência:** resposta `202` com `ACIONADA`. Alguns segundos depois, o `GET` mostra `NOTIFICADA`.
   No portal, mostrar no log do App Service o aviso com o telefone mascarado.
5. **Golpe:** colar "INSS: seu benefício será bloqueado, informe sua senha no link". A resposta vem como
   `PROVAVEL_GOLPE`, com o canal oficial e as fontes.
6. **Atendimento:** "onde tem hospital perto" ou `GET /api/v1/atendimentos/proximos?tipo=HOSPITAL&acessivel=true`.
   Abrir o link do Google Maps.
7. **LGPD:** `DELETE /api/v1/auth/eu` e, com o mesmo token, qualquer rota responde `401`.

## Java Advanced

### Modelagem e relacionamentos (15 pts)

| Entidade | Relação | Por quê |
|---|---|---|
| `Usuario` | papel `CIDADAO` ou `CUIDADOR` | Uma conta só para todo o ecossistema (a API .NET usa o mesmo JWT) |
| `PerfilEmergencia` | `@OneToOne` com o cidadão (obrigatório); `@ManyToOne` com o cuidador (opcional) | Um perfil por cidadão; um cuidador cuida de vários; quem mora sozinho não precisa de cuidador |
| `ContatoEmergencia` | `@ManyToOne` com o perfil; o perfil tem `@OneToMany` com `cascade` e `orphanRemoval` | Os contatos só existem dentro do perfil; até 10, com prioridade |
| `Emergencia` | `@ManyToOne` com o perfil | Histórico de pedidos de ajuda, com status e horários |
| `AvisoEmergencia` | `@ManyToOne` com a emergência | Prova de quem foi avisado e quando |
| `InteracaoVoz`, `VerificacaoGolpe`, `PontoAtendimento` | independentes | Histórico de voz, estatística anônima de golpes e locais do OpenStreetMap |

Código em `model/`; o esquema é versionado no Flyway (`src/main/resources/db/migration`, V1 a V8).

**Perguntas prováveis:**
- *Por que `LAZY` em tudo?* Para não carregar o grafo inteiro a cada consulta. Onde precisa, a consulta usa
  `join fetch` (`repository/PerfilEmergenciaRepository.java`).
- *Como evita duas emergências com toque duplo?* `PESSIMISTIC_WRITE` no perfil antes de criar. Um segundo toque em
  até 2 minutos devolve a mesma emergência (`service/EmergenciaService.java`).
- *Por que DTOs?* As entidades nunca saem da API (senha, relações lazy). O ArchUnit garante que controllers só
  recebem e devolvem DTOs (`src/test/.../arquitetura`).
- *Paginação?* `Pageable` nas listagens, com ordenação limitada a campos públicos (`sort=cidadao.senhaHash` dá 400).

### Fluxo de autenticação JWT (20 pts)

1. `POST /api/v1/auth/cadastro`: a senha é gravada com BCrypt (`{bcrypt}...`, formato delegado do Spring
   Security), e o consentimento da LGPD é obrigatório.
2. `POST /api/v1/auth/login`: confere a senha e emite o JWT **HS256** (`security/EmissorToken.java`) com `sub` (id),
   `role`, `name`, `iss=voz-e-via-java`, `aud=voz-e-via` e validade de 2 horas.
3. O app manda `Authorization: Bearer <token>`. O Spring Security, como **resource server**, valida assinatura,
   validade, `iss`, `aud` e se a conta foi excluída (`config/SecurityConfig.java`,
   `security/ContaExcluidaValidator.java`).
4. O `role` vira `ROLE_CIDADAO` ou `ROLE_CUIDADOR`, e o `@PreAuthorize` nos controllers barra o papel errado (403).
5. A API .NET valida o mesmo token com o mesmo segredo (`JWT_SECRET`, só em variável de ambiente).

**Perguntas prováveis:**
- *Por que HS256 e não RS256?* São só dois serviços do mesmo grupo, que compartilham o segredo com segurança. Com
  mais emissores ou terceiros, o caminho é RS256 com JWKS. O algoritmo já é configurável (`JWT_ALGORITMO`).
- *E força bruta?* 5 erros seguidos bloqueiam o login por 15 minutos (429), e a mensagem é a mesma para e-mail
  inexistente e senha errada (`service/LimitadorTentativasLogin.java`).
- *Como invalida o token de quem excluiu a conta?* O id vai para `contas_excluidas`, e um validador recusa o
  token na hora, sem esperar ele expirar.
- *CSRF?* Continua ativo, mas requisições com header `Authorization` não precisam do token CSRF: um site
  malicioso não consegue fazer o navegador enviar esse header. Sem `Authorization`, vale o token CSRF em cookie
  (README, seção CSRF).

### Spring AI (15 pts)

- Starter `spring-ai-starter-model-openai` apontado para o endpoint **compatível com OpenAI da OCI Generative AI**,
  modelo `meta.llama-3.3-70b-instruct`.
- **RAG:** antes de chamar o modelo, um índice TF-IDF (`ai/IndiceTfIdf.java`) busca na base curada
  (`resources/conhecimento/golpes.json` e os exemplos de intenção) os documentos mais parecidos com o texto. Eles
  entram no prompt (`resources/prompts`).
- **Saída estruturada:** `.entity(...)` converte a resposta num record Java. Se o formato vier errado, a resposta é
  descartada.
- **Regras como piso de segurança** (`ai/RegrasGolpe.java`, `ai/RegrasIntencao.java`): um pedido de senha ou
  código nunca sai como "sem sinais de risco", e um socorro nunca se perde, mesmo que o modelo erre. Sem chave ou
  com a IA fora do ar, as regras respondem sozinhas.
- O canal oficial vem sempre da base curada, nunca do modelo (o modelo não inventa telefone).

**Perguntas prováveis:**
- *Por que TF-IDF e não embeddings?* A base é pequena e curada. O TF-IDF roda em memória, sem custo e sem
  depender de outro serviço, e é fácil explicar por que um documento foi escolhido. Com uma base grande, troca-se
  por embeddings e um vector store, mantendo a mesma interface.
- *Como avaliaram?* `./gradlew avaliarIa` roda frases e mensagens rotuladas contra o modelo real e gera
  `build/reports/avaliacao-golpes/ia-e-regras.md` e `build/reports/avaliacao-intencoes/ia-e-regras.md`. As regras
  sozinhas são avaliadas no build normal (relatório `somente-regras`). Os resultados estão no README, em
  "Avaliação".
- *Limitações:* o modelo pode errar em golpes novos que não estão na base; por isso há as regras e a classificação
  "não é possível avaliar". A voz é transcrita no celular, então sotaque e ruído afetam o texto que chega.

### Arquitetura distribuída: opção A, RabbitMQ (15 pts)

```
POST /emergencias -> grava ACIONADA -> commit -> @Async publica no exchange vozviva.emergencias
   -> fila vozviva.emergencias.acionadas -> consumidor avisa os contatos -> NOTIFICADA
   falhou nas 3 tentativas (esperas de 1 s e 2 s) -> vozviva.emergencias.dlx -> vozviva.emergencias.dlq
```

- A publicação usa `@TransactionalEventListener(AFTER_COMMIT)` com `@Async`
  (`messaging/PublicadorEmergencia.java`): só publica o que foi gravado, e o 202 não espera o broker.
- Retry com espera crescente (3 tentativas) e DLQ (`config/RabbitEmergenciaConfig.java`).
- **Reenvio:** uma tarefa agendada republica as emergências que ficaram `ACIONADA` (broker fora do ar, mensagem
  perdida). Depois de 5 reenvios, vira `FALHA_NO_AVISO`, e o app orienta ligar 192
  (`messaging/ReenvioEmergencias.java`).
- A fonte da verdade é o banco: perder a fila não perde a emergência.

**Perguntas prováveis:**
- *Por que assíncrono?* O cidadão em perigo não pode esperar o envio para vários contatos. Ele recebe a confirmação
  na hora, e o aviso segue mesmo se um contato falhar.
- *E se o consumidor processar a mesma mensagem duas vezes?* O processamento é idempotente: só emergências
  `ACIONADA` são processadas, com a linha travada durante o envio (`service/AvisosDeEmergencia.java`).
- *O aviso chega por SMS?* Hoje o aviso é gravado em `avisos_emergencia` e registrado no log com o telefone
  mascarado (`service/NotificadorPorRegistro.java`). O SMS ou WhatsApp entra como outra implementação da interface
  `NotificadorContatos`, sem mudar o resto. Diga isso com clareza se perguntarem.

### Como colabora com o cidadão (15 pts)

Fale da pessoa, não da tecnologia: a idosa que cai em casa e pede ajuda falando; o cadeirante que acha o hospital
com acessibilidade; o aposentado que confere a mensagem do "INSS" antes de passar a senha. Os números estão no
README, em "O problema" e em "Impacto e modelo de negócio".

## IA generativa (banca de Disruptive Architectures)

- **Adaptação a uma mudança de requisito (25 pts).** Respostas prontas:
  - *Novo tipo de golpe:* entra um documento em `conhecimento/golpes.json`. O RAG passa a encontrá-lo, sem treinar
    nada.
  - *Nova intenção de voz:* novo valor no enum, exemplos em `ExemplosIntencao`, uma regra mínima em
    `RegrasIntencao` e a ação que o app deve abrir, com frases rotuladas novas na avaliação.
  - *Trocar de provedor de IA:* só `base-url`, modelo e chave no `application.yml`, porque o Spring AI abstrai o
    cliente.
- **Uso responsável:** a chave só existe em variável de ambiente; o texto da mensagem verificada não é gravado; o
  histórico de voz não guarda a localização.

## DevOps (banca de Cloud)

- **Arquitetura:** `docs/arquitetura.drawio` (fluxos 1 a 7). App Service com container, Azure SQL, ACR, RabbitMQ
  em ACI só com IP privado na VNet.
- **Perguntas prováveis:**
  - *Como o App Service puxa a imagem?* Por identidade gerenciada com `AcrPull`, sem senha.
  - *Usuário do banco?* `vozviva_app`, contido, com privilégio mínimo; o administrador serve só para provisionar.
  - *O que acontece num merge?* O build roda os testes, publica o JUnit e a cobertura, gera a imagem com a tag do
    build e a release a publica e confere o health.
  - *Por que não guardar o RabbitMQ no Azure Files?* A documentação do RabbitMQ desaconselha sistema de arquivos de
    rede; a fonte da verdade é o banco, e o reenvio recupera o que estava na fila.

## Compliance, QA & Tests

- Backlog e casos de teste em `docs/azure-boards/` (como importar no README, em "Deploy na Azure").
- **Operar o Test Plans (20 pts):** rodar ao vivo 2 ou 3 casos manuais (por exemplo, "Cidadão cria o próprio
  perfil" e "Cidadão exclui a conta"), marcando passo a passo como Passed ou Failed.
- **Relatórios analíticos (20 pts):** Test Plans → Progress report e, no build, a aba Tests com o resultado dos
  testes automatizados.
- **Perguntas prováveis:**
  - *Diferença entre Feature e PBI?* A Feature é a capacidade entregue ao usuário; o PBI é uma história que cabe
    numa sprint, com critério de aceite testável.
  - *Caixa-preta?* Os casos usam só a API (entradas e saídas), sem olhar o código.
  - *Por que casos negativos?* Para provar que a API recusa o que deve recusar (papel errado, dados inválidos,
    limites) com a mensagem certa.

## Números para ter na ponta da língua

- 32,1 milhões de pessoas com 60 anos ou mais e 14,4 milhões de pessoas com deficiência (Censo 2022, IBGE).
- 68,8% dos moradores urbanos vivem em ruas sem rampa.
- Mais de 370 testes automatizados, com cobertura mínima de 80% verificada no build.
- Socorro: resposta 202 imediata; aviso em segundo plano com 3 tentativas, DLQ e até 5 reenvios.
