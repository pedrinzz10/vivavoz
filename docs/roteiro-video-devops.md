# Roteiro do vídeo de DevOps (Azure DevOps + Azure)

Roteiro para gravar o vídeo da disciplina **DevOps Tools & Cloud Computing** cobrindo os 10 itens obrigatórios (cada
item omitido custa −20) e os itens que tiram ponto. Tem duas partes:

1. **Preparação** (antes de gravar, fora do vídeo): deploy, conexões da pipeline e uma execução de teste.
2. **Gravação** (vídeo contínuo, sem cortes): o que mostrar e o que falar em cada item.

Nomes usados (sufixo `rm562312`):

| Recurso | Nome |
|---|---|
| Grupo de recursos | `rg-vozviva` |
| API Java (App Service) | `app-vozviva-rm562312` |
| API .NET (App Service) | `app-zeladoria-rm562312` |
| Azure SQL | `sql-vozviva-rm562312` (bancos `vozviva` e `zeladoria`) |
| Container Registry | `acrvozvivarm562312` (`acrvozvivarm562312.azurecr.io`) |
| Organização / projeto | `https://dev.azure.com/vozviva` / `vozviva` |

> **Cloud Shell:** a sessão perde as variáveis e a pasta `~/vivavoz` quando reinicia. Cada bloco abaixo começa
> do zero. Cole **uma linha por vez** (linhas longas quebram ao colar).

---

## Parte 1 – Preparação (antes de gravar)

### 1.1 IA na OCI (Cloud Shell da OCI, região São Paulo)

```bash
git clone -q https://github.com/pedrinzz10/vivavoz.git ~/vivavoz
```
```bash
cd ~/vivavoz
```
```bash
ORCAMENTO_EMAIL=RM562312@fiap.com.br ./scripts/provisionar-oci.sh
```

No fim, o teste deve mostrar `o modelo respondeu (HTTP 200)`. Para levar a chave à Azure, mostre o valor (fora da
gravação) e copie:

```bash
cat ~/.vozviva/oci-genai.env
```

### 1.2 Infraestrutura na Azure (Cloud Shell da Azure, Bash)

Gere as senhas e **guarde num gerenciador de senhas** antes de seguir (rodar o script de novo pede as mesmas):

```bash
git clone -q https://github.com/pedrinzz10/vivavoz.git ~/vivavoz
```
```bash
cd ~/vivavoz
```
```bash
export SUFIXO=rm562312 LIBERAR_MEU_IP=sim
```
```bash
export SQL_ADMIN_SENHA="$(openssl rand -hex 10)Aa1#"
```
```bash
export APP_SQL_SENHA="$(openssl rand -hex 10)Bb2#"
```
```bash
export RABBITMQ_SENHA="$(openssl rand -hex 16)"
```
```bash
export JWT_SECRET="$(openssl rand -hex 32)"
```
```bash
export USUARIOS_TESTE_SENHA="<uma senha com letra e número>"
```
```bash
read -rs OCI_GENAI_API_KEY && export OCI_GENAI_API_KEY
```
(cole a chave da OCI e aperte Enter; nada aparece na tela)

```bash
printf 'SQL admin: %s\nSQL app: %s\nRabbitMQ: %s\nJWT: %s\n' "$SQL_ADMIN_SENHA" "$APP_SQL_SENHA" "$RABBITMQ_SENHA" "$JWT_SECRET"
```
(copie para o gerenciador de senhas e limpe a tela com `clear`)

```bash
./scripts/provisionar-azure.sh
```

Leva de 15 a 20 minutos e termina com o resumo das URLs. Confira:

```bash
az resource list -g rg-vozviva --query "[].{nome:name,tipo:type}" -o table
```

### 1.3 Conexões da pipeline (navegador, Azure DevOps)

Em `https://dev.azure.com/vozviva/vozviva/_settings/adminservices` → **New service connection**:

1. **Azure Resource Manager** → **Workload identity federation (automatic)**
   - Subscription: `2tdspw-rm562312-pedrooliveira`; Resource group: `rg-vozviva`
   - Service connection name: **`vozviva-azure`**
   - Marque **Grant access permission to all pipelines** → Save
2. **Docker Registry** → **Azure Container Registry** (autenticação *Workload identity federation*, se pedir)
   - Subscription: a mesma; Azure container registry: `acrvozvivarm562312`
   - Service connection name: **`vozviva-acr`**
   - Marque **Grant access permission to all pipelines** → Save

### 1.4 Grupo de variáveis e professor (Cloud Shell da Azure)

```bash
cd ~/vivavoz
```
```bash
export ORGANIZACAO=vozviva REVISOR_EMAIL=RM562312@fiap.com.br
```
```bash
export WEBAPP_NAME=app-vozviva-rm562312
```
```bash
export ACR_LOGIN_SERVER=acrvozvivarm562312.azurecr.io
```
```bash
export PROFESSOR_DEVOPS_EMAIL="<e-mail do professor>"
```
```bash
./scripts/configurar-azure-devops.sh
```

O professor entra com **Basic** na organização e em **Project Administrators** no projeto (sem acesso: −70).

### 1.5 Primeira execução da pipeline (ensaio)

1. `https://dev.azure.com/vozviva/vozviva/_build` → **vozviva-ci-cd** → **Run pipeline** (branch `main`).
2. Se aparecer **"This pipeline needs permission to access a resource"**, clique em **View → Permit** (grupo de
   variáveis, service connections e environment `producao`). **Precisa ser antes do vídeo**: se pedir permissão
   na gravação, a pipeline não roda "de forma automática".
3. Espere os dois estágios ficarem verdes (6 a 10 minutos). Confira:

```bash
curl -s https://app-vozviva-rm562312.azurewebsites.net/actuator/health/readiness
```
Resposta esperada: `{"status":"UP"}`.

### 1.6 Deixar abertos antes de apertar o REC

| Aba | Endereço |
|---|---|
| README no Azure Repos | `https://dev.azure.com/vozviva/vozviva/_git/vozviva` |
| Portal Azure, grupo de recursos | `https://portal.azure.com` → Resource groups → `rg-vozviva` |
| Boards | `https://dev.azure.com/vozviva/vozviva/_boards/board` |
| Pipelines | `https://dev.azure.com/vozviva/vozviva/_build` |
| Swagger publicado | `https://app-vozviva-rm562312.azurewebsites.net/swagger-ui.html` |
| Query editor do Azure SQL | Portal → SQL databases → `vozviva` → **Query editor** (login `vozvivaadmin` e `SQL_ADMIN_SENHA`; se pedir, clique em *Allowlist IP*) |

Checklist técnico: gravação em **1280×720 ou mais**, microfone testado, **narração falada** (sem legendas),
notificações do sistema desligadas, nenhuma senha na tela.

---

## Parte 2 – Gravação (sem cortes)

Tempo estimado: 15 a 20 minutos, a maior parte com a pipeline rodando (comente o que cada etapa faz).

### Item 1 – README, solução e arquitetura (≈ 2 min)

**Mostrar:** no Azure Repos, o README: "O problema e quem a solução atende", "A solução" e "Arquitetura" (diagrama).

**Falar:** "O VozViva ajuda idosos e pessoas com deficiência: pedido de socorro com aviso à rede de apoio, assistente
contra golpes com IA e atendimento acessível mais próximo. Na arquitetura, o app chama a API Java por HTTPS com JWT;
a API grava no Azure SQL, publica as emergências no RabbitMQ e consulta a IA na OCI. A API .NET faz a zeladoria com
o mesmo token. O Azure DevOps leva cada merge na main até o App Service."

### Item 2 – Recursos criados pelos scripts (≈ 1 min)

**Mostrar:** `scripts/provisionar-azure.sh` no Repos (rolar rápido) e, no portal, o grupo `rg-vozviva`: os dois
App Services, o plano B1, o Azure SQL com os bancos `vozviva` e `zeladoria`, o Container Registry, o Container
Instance do RabbitMQ e a rede virtual.

**Falar:** "Tudo foi criado por este script Azure CLI, que pode rodar de novo sem duplicar nada. O RabbitMQ não tem
IP público: só a API alcança, pela rede virtual."

### Item 3 – Nova tarefa no Boards (≈ 30 s)

**Fazer:** Boards → **New item → Task**, título **"Descrever no Swagger que o cidadão também mantém o perfil"** →
Save. **Anote o número da tarefa** (ex.: 101).

### Item 4 – Nova branch e alteração no código-fonte (≈ 2 min)

**Fazer:**
1. Na tarefa, **Development → Create a branch**: nome `feature/swagger-perfil`, base `main` (a branch já fica
   ligada à tarefa).
2. Repos → branch `feature/swagger-perfil` → arquivo
   `src/main/java/br/com/fiap/vozviva/config/OpenApiConfig.java` → **Edit**. Na linha 81, troque:

```java
new Tag().name(TAG_PERFIL).description("Perfil de emergência mantido pelo cuidador"),
```
por:
```java
new Tag().name(TAG_PERFIL).description("Perfil de emergência mantido pelo cidadão ou pelo cuidador"),
```
3. **Commit** com a mensagem `Swagger: o cidadão também mantém o perfil #101` (troque 101 pelo número da tarefa).

**Falar:** "A alteração é no código-fonte, não no README: a descrição do módulo de perfil no Swagger estava
desatualizada, porque hoje o próprio cidadão também mantém o perfil."

### Item 5 – Merge na main e pipelines automáticas (≈ 1 min)

**Fazer:** **Create a pull request** → para `main` → confira a tarefa em *Work items* → **Create** → **Approve** →
**Complete**, marcando **Complete associated work items after merging** e **Delete branch** → **Complete merge**.

**Falar:** "A main é protegida: só recebe código por pull request, com revisor e tarefa vinculada. O merge dispara
a pipeline sozinho."

### Item 6 – Execução completa das pipelines (≈ 6 a 10 min)

**Mostrar:** Pipelines → a execução que acabou de começar (gatilho *Individual CI*, da `main`):

- Estágio **Build e testes (CI)**: Gradle com os testes (unitários, integração com Testcontainers, arquitetura e
  contrato OpenAPI), cobertura JaCoCo, pacote e imagem enviada ao ACR.
- Estágio **Deploy no App Service (CD)**: atualiza o App Service com a imagem do build e confere a readiness
  (`/actuator/health/readiness`).

### Item 7 – Artefatos da CI (≈ 1 min)

**Mostrar:** na execução:

- aba **Tests**: os testes JUnit publicados (todos aprovados);
- aba **Code Coverage**: a cobertura do JaCoCo;
- **Related → Published (1)** → artefato **`vozviva`**: `vozviva.jar`, `openapi.json` e `script-bd.sql`;
- no portal, o Container Registry → Repositories → `vozviva-api` com a tag do número do build.

### Item 8 – Alteração publicada na nuvem (≈ 30 s)

**Mostrar:** `https://app-vozviva-rm562312.azurewebsites.net/swagger-ui.html` (atualize a página) → o módulo de
perfil agora diz "**mantido pelo cidadão ou pelo cuidador**".

### Item 9 – CRUD em três tabelas, conferindo **direto no banco** (≈ 4 min)

Regra da disciplina: **não use o GET para provar que gravou; use SELECT** no Query editor depois de cada operação.

**9.1 Tabela `usuarios`** (sem token: cadastro e login são públicos)

| Operação | Swagger | Corpo |
|---|---|---|
| **C** | `POST /api/v1/auth/cadastro` | `{"nome": "Maria da Silva", "email": "maria.video@vozviva.dev", "senha": "girassol2026", "papel": "CIDADAO", "consentimento": true}` |
| login | `POST /api/v1/auth/login` | `{"email": "maria.video@vozviva.dev", "senha": "girassol2026"}` → copie o `accessToken` → **Authorize** |
| **R** | `GET /api/v1/auth/eu` | — |
| **U** | `PUT /api/v1/auth/eu` | `{"nome": "Maria da Silva Souza"}` |

```sql
SELECT id, nome, email, papel, consentimento_em FROM usuarios WHERE email = 'maria.video@vozviva.dev';
```

(O **D** de `usuarios` fica no fim, no item 9.4, porque apagar a conta apaga também o perfil e os contatos.)

**9.2 Tabela `perfis_emergencia`** (com o token da Maria)

| Operação | Swagger | Corpo |
|---|---|---|
| **C** | `POST /api/v1/perfis-emergencia` | `{"mobilidade": "CADEIRANTE", "condicoesSaude": "Hipertensão", "medicamentos": "Losartana 50 mg", "observacoes": "Mora sozinha"}` |
| **R** | `GET /api/v1/perfis-emergencia/meu` | — (anote o `id`) |
| **U** | `PUT /api/v1/perfis-emergencia/{id}` | `{"mobilidade": "ANDADOR", "condicoesSaude": "Hipertensão", "medicamentos": "Losartana 100 mg", "observacoes": "Mora sozinha"}` |

```sql
SELECT id, cidadao_id, mobilidade, medicamentos, atualizado_em FROM perfis_emergencia ORDER BY id DESC;
```

**9.3 Tabela `contatos_emergencia`** (com o `id` do perfil)

| Operação | Swagger | Corpo |
|---|---|---|
| **C** | `POST /api/v1/perfis-emergencia/{perfilId}/contatos` | `{"nome": "Ana Souza", "telefone": "(11) 91234-5678", "parentesco": "Filha", "prioridade": 1}` |
| **R** | `GET /api/v1/perfis-emergencia/{perfilId}/contatos` | — (anote o `id` do contato) |
| **U** | `PUT /api/v1/perfis-emergencia/{perfilId}/contatos/{contatoId}` | `{"nome": "Ana Souza", "telefone": "(11) 98888-7777", "parentesco": "Filha", "prioridade": 2}` |
| **D** | `DELETE /api/v1/perfis-emergencia/{perfilId}/contatos/{contatoId}` | — |

```sql
SELECT id, perfil_id, nome, telefone, prioridade FROM contatos_emergencia ORDER BY id DESC;
```

(repare que o telefone fica gravado como `+5511912345678`)

**9.4 Os deletes que faltam**

| Operação | Swagger | Conferência |
|---|---|---|
| **D** perfil | `DELETE /api/v1/perfis-emergencia/{id}` | `SELECT * FROM perfis_emergencia WHERE id = <id>;` → nenhuma linha |
| **D** usuário | `DELETE /api/v1/auth/eu` | `SELECT * FROM usuarios WHERE email = 'maria.video@vozviva.dev';` → nenhuma linha |

**Falar:** "Cada operação foi conferida direto no Azure SQL com SELECT. A exclusão da conta atende a LGPD: apaga a
conta e tudo o que depende dela, e o token antigo para de valer na hora."

### Item 10 – Tarefa concluída com os links (≈ 30 s)

**Mostrar:** Boards → a tarefa do item 3 → estado **Done** → seção **Development** com a **branch**, o **commit**
e o **pull request**. Se a tarefa não fechou sozinha, mude o estado para **Done** na hora.

**Falar (fechamento):** "A tarefa saiu do Boards, virou branch, commit e pull request, passou pelos testes na
pipeline e foi publicada na Azure automaticamente."

---

## Parte 3 – Depois de gravar

1. Assista ao vídeo já publicado no YouTube (público ou não listado) antes de entregar: 720p, áudio claro,
   todos os 10 itens.
2. A alteração feita no vídeo ficou só no Azure Repos. Traga para o GitHub (pull do Azure e PR no GitHub).
3. **PDF da entrega** (nome `Grupo_rm562312.pdf`): folha de rosto com o nome do grupo, RM e nome completo dos
   integrantes; link da organização (`https://dev.azure.com/vozviva`); link do vídeo.
4. Mantenha tudo no ar até a correção (30/11). Para parar a cobrança depois: `./scripts/remover-azure.sh`.

## O que tira ponto e onde está coberto

| Item | Desconto | Coberto por |
|---|---|---|
| Código, `azure-pipeline.yml`, scripts, `script-bd.sql`, Dockerfile, pastas `scripts` e `dockerfiles` | −70 a −10 | Repositório (já atendido) |
| App ou banco em localhost | −70 | Preparação 1.2 |
| Sem acesso ao Azure DevOps | −70 | Preparação 1.4 (convite do professor) |
| Branch principal desprotegida | −20 | Políticas da main (já atendido) |
| Sem variáveis de ambiente / senhas no código | −20 | Configurações do App Service e grupo de variáveis |
| Sem testes automáticos na CI | −20 | Item 6 e 7 |
| Não criou branch para a tarefa | −10 | Item 4 |
| Alteração no README em vez do código | −20 | Item 4 (`OpenApiConfig.java`) |
| CRUD sem SELECT no banco em nuvem | −30 | Item 9 |
| Vídeo sem narração, abaixo de 720p ou com áudio ruim | −30 | Checklist da Preparação 1.6 |
| Item obrigatório omitido no vídeo | −20 cada | Itens 1 a 10 |
| Entrega fora do prazo (19/11, 23h55) | −70 | Parte 3 |
