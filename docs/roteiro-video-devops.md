# Roteiro do vídeo de DevOps

Guia passo a passo para a entrega de **DevOps Tools & Cloud Computing**. O vídeo mostra tudo, do zero: a solução,
a criação dos recursos na Azure, a organização no Azure DevOps com uma alteração indo até a nuvem, o CRUD e a
tarefa concluída. Cada comando vem com uma explicação do que ele faz.

## Visão geral

| Fase | O que acontece | Tempo |
|---|---|---|
| **A. Antes de gravar** (fora do vídeo) | Cria a chave da IA na Oracle, guarda as senhas num arquivo e deixa as abas prontas | 20 min |
| **B. Gravação** (uma gravação só, do início ao fim) | 5 partes: solução → recursos na Azure → organização e pipelines → CRUD → tarefa concluída | ≈ 45 min gravados |
| **C. Edição** | Acelerar as duas esperas (script e pipeline), sem cortar nada | 15 min |
| **D. Depois de editar** | Conferir o vídeo, montar o PDF e entregar | 20 min |

**Meta: vídeo final de 18 a 20 minutos.** A gravação é contínua, mas o script da Azure (15 a 20 min) e a pipeline
(8 a 12 min) são espera. Você explica o que está acontecendo nos primeiros 1 a 2 minutos de cada espera e, na
edição, **acelera o resto** (fica na tela, só mais rápido). Nada é cortado: o professor vê tudo acontecendo.

Ordem da gravação e tempo de cada parte **no vídeo final**:

| Parte | O que mostra | Itens obrigatórios | Tempo |
|---|---|---|---|
| 1. Solução e arquitetura | README, problema, solução e o desenho com os recursos | Apresentar o README e o desenho | 3 min |
| 2. Recursos na Azure | O script criando tudo (espera acelerada) e o resultado no portal | Recursos criados pelos scripts | 3 min |
| 3. Organização e pipelines | O projeto, a tarefa, a branch, a alteração no código, o merge, o build e o deploy (espera acelerada) e o resultado na nuvem | Tarefa, branch e código, merge, pipelines, artefatos, alteração publicada | 8 a 9 min |
| 4. CRUD | Três tabelas, conferidas com `SELECT` no banco | CRUD em pelo menos duas tabelas | 4 a 5 min |
| 5. Final | A tarefa concluída com os links | Tarefa concluída com os links | 30 s |

Para caber no tempo: fale de forma direta, deixe as abas abertas na ordem e **deixe os JSON do CRUD num bloco de
notas** (só os JSON, nada de senha) para copiar e colar.

Nomes que vão aparecer (o sufixo `rm562312` deixa os nomes únicos na Azure):

| O quê | Nome |
|---|---|
| Grupo de recursos (a "pasta" com tudo na Azure) | `rg-vozviva` |
| API Java | `app-vozviva-rm562312` |
| API .NET | `app-zeladoria-rm562312` |
| Banco de dados (Azure SQL, no Chile) | `sql-vozviva-rm562312-chilecentral`, com os bancos `vozviva` e `zeladoria` |
| Registro de imagens (Container Registry) | `acrvozvivarm562312` |
| Azure DevOps | organização `vozviva`, projeto `vozviva` |

### Como usar o Cloud Shell

O Cloud Shell é um terminal no navegador que já vem logado na sua conta. Há dois: o da **Oracle (OCI)**, aberto
pelo ícone `>_` no topo do console da OCI, e o da **Azure**, aberto pelo ícone `>_` no topo do portal da Azure
(escolha **Bash**).

- Cole **um comando por vez** e aperte Enter. Linhas longas costumam quebrar quando se cola várias de uma vez.
- Copie **só o que está dentro dos blocos cinza**, nunca o texto explicativo.
- Quando o Cloud Shell reinicia, ele **esquece as variáveis, a pasta baixada e os arquivos criados**. Por isso a
  Fase A deve ser feita logo antes de gravar, sem fechar o Cloud Shell da Azure.
- Aumente a fonte do Cloud Shell (engrenagem → **Text size → Large**) para o texto ficar legível no vídeo.

---

## Fase A – Antes de gravar (fora do vídeo)

Aqui ficam só as coisas que **mostram senhas na tela**. Nada disso pode aparecer no vídeo.

### A1. Criar a chave da IA na Oracle (Cloud Shell da **OCI**)

A IA fica na Oracle, não na Azure, então ela não faz parte da demonstração dos recursos da Azure. Se o Cloud
Shell da OCI ainda tiver a pasta `~/vivavoz`, use `git -C ~/vivavoz pull -q` no lugar do `git clone`.

```bash
git clone -q https://github.com/pedrinzz10/vivavoz.git ~/vivavoz
```
**O que faz:** baixa o projeto do GitHub para a pasta `vivavoz` do Cloud Shell.

```bash
cd ~/vivavoz
```
**O que faz:** entra na pasta do projeto, para os próximos comandos encontrarem os scripts.

```bash
ORCAMENTO_EMAIL=RM562312@fiap.com.br ./scripts/provisionar-oci.sh
```
**O que faz:** confere se o modelo `meta.llama-3.3-70b-instruct` existe em São Paulo, cria um compartimento
`vozviva` (uma "pasta" para organizar), cria a **credencial da IA**, cria um alerta de gastos que avisa no seu
e-mail e faz uma pergunta de teste ao modelo.

A credencial normalmente é uma API key do Generative AI. A nossa conta não permite API keys (o limite
`api-key-count` é zero), então o script cria no lugar uma **chave de assinatura para o seu usuário** da OCI, e a
API Java assina cada chamada ao modelo com ela. Na saída aparece `usando a assinatura do usuário`, e isso é o
esperado.
**Resultado esperado:** a última parte mostra `o modelo respondeu (HTTP 200)`. O orçamento é de US$ 300 por
mês (os créditos da conta), com alertas por e-mail em 50% e 100%.

```bash
base64 -w 64 ~/.vozviva/oci-genai.env
```
**O que faz:** mostra o arquivo com as credenciais da IA codificado em Base64, num bloco de linhas curtas.
**Copie o bloco inteiro** para a etapa A2. Ele contém a chave privada: não mostre no vídeo nem cole em nenhum chat.

### A2. Guardar as senhas num arquivo (Cloud Shell da **Azure**, em Bash)

As senhas vão para o arquivo `~/vozviva-segredos.env`. No vídeo, um único comando carrega esse arquivo, e nenhuma
senha aparece na tela.

```bash
umask 077
```
**O que faz:** faz com que os arquivos criados a seguir só possam ser lidos por você.

```bash
echo "SQL_ADMIN_SENHA='$(openssl rand -hex 10)Aa1#'" > ~/vozviva-segredos.env
```
**O que faz:** gera uma senha aleatória e forte para o administrador do banco e cria o arquivo com ela. O `>` cria
o arquivo (ou apaga o que havia nele).

```bash
echo "APP_SQL_SENHA='$(openssl rand -hex 10)Bb2#'" >> ~/vozviva-segredos.env
```
**O que faz:** gera a senha do usuário que a API usa no banco e acrescenta no arquivo. O `>>` acrescenta no fim,
sem apagar o que já estava lá.

```bash
echo "RABBITMQ_SENHA='$(openssl rand -hex 16)'" >> ~/vozviva-segredos.env
```
**O que faz:** gera a senha da fila de mensagens (RabbitMQ), usada pelo botão de emergência.

```bash
echo "JWT_SECRET='$(openssl rand -hex 32)'" >> ~/vozviva-segredos.env
```
**O que faz:** gera o segredo que assina os tokens de login. As duas APIs recebem o mesmo valor, por isso o mesmo
login vale nas duas.

```bash
sed '/^$/q' | base64 -d >> ~/vozviva-segredos.env
```
**O que faz:** espera você colar o bloco copiado na etapa A1, decodifica e acrescenta as credenciais da IA no
arquivo. Cole o bloco, aperte **Enter** e depois **Enter de novo numa linha vazia** para terminar.

```bash
read -rs K && echo "USUARIOS_TESTE_SENHA='$K'" >> ~/vozviva-segredos.env
```
**O que faz:** pede a senha dos usuários de teste que a API cria (`cidadao.teste@vozviva.dev`, os gestores etc.).
Digite uma senha **só com letras e números** e aperte Enter (nada aparece na tela).

```bash
cat ~/vozviva-segredos.env
```
**O que faz:** mostra o arquivo, para você conferir. Não precisa guardar as senhas em outro lugar: na Parte 2, o
script grava todas no **Key Vault** da Azure, e é de lá que você copia a senha do banco na Parte 4. Depois limpe a
tela:

```bash
clear
```
**O que faz:** apaga o que estava na tela, para as senhas não ficarem expostas.

Se o Cloud Shell reiniciar antes da gravação e o arquivo sumir, refaça esta etapa: como a Azure ainda não foi
criada, senhas novas servem do mesmo jeito.

### A3. Atualizar o Azure Repos com o GitHub

O vídeo mostra o código e o README a partir do Azure Repos, então ele precisa estar igual ao GitHub. **Peça para o
Claude fazer a sincronização**: ele envia a branch `sync/github` e abre o PR. Você abre o PR no navegador, clica em
**Approve** e depois em **Complete**, escolhendo **Rebase and fast-forward**.

### A4. Antes de apertar o REC

Deixe estas abas abertas, na ordem em que vão aparecer:

| Aba | Endereço |
|---|---|
| README no Azure Repos | `https://dev.azure.com/vozviva/vozviva/_git/vozviva` |
| Desenho no draw.io | [abrir `docs/arquitetura.drawio`](https://app.diagrams.net/#Uhttps%3A%2F%2Fraw.githubusercontent.com%2Fpedrinzz10%2Fvivavoz%2Fmain%2Fdocs%2Farquitetura.drawio) |
| Portal Azure com o Cloud Shell aberto | `https://portal.azure.com` (o mesmo Cloud Shell da etapa A2) |
| Azure DevOps | `https://dev.azure.com/vozviva/vozviva` (Summary com a descrição e o README; dashboard em Overview → Dashboards) |
| Work items do Boards | `https://dev.azure.com/vozviva/vozviva/_workitems` |

**Checklist técnico:** resolução de 1280×720 ou mais, microfone testado, **narração falada** (legendas no lugar
da voz tiram 30 pontos), notificações desligadas e nenhuma senha aberta em outra janela.

**Recomendação:** se der tempo, faça um ensaio completo antes, sem gravar: rode as Partes 2 e 3, confira que a
pipeline fica verde e apague tudo com `./scripts/remover-azure.sh`. Custa poucos centavos de crédito e evita
regravar 45 minutos por causa de um erro. Também serve para treinar as falas dentro do tempo.

---

## Fase B – Gravação (uma gravação só, sem pausar)

Cada passo diz o que **fazer**, o que **mostrar** e uma sugestão do que **falar**. Não pause a gravação nas
esperas: deixe gravando, e a aceleração é feita depois, na Fase C. Os tempos entre parênteses são do vídeo final.

### Parte 1 – Solução e desenho da arquitetura (≈ 3 min)

**Mostrar:** o README no Azure Repos e depois o desenho no draw.io. O professor avalia **desenho, fluxo e
legenda** (10 pontos).

1. **O problema**, na seção "O problema e quem a solução atende":
   > "O Brasil tem 32 milhões de pessoas com mais de 60 anos e 14 milhões de pessoas com deficiência, e 68,8% dos
   > moradores urbanos vivem em ruas sem rampa. Elas ficam presas em casa e mais expostas a golpes."
2. **A solução**, na tabela de "A solução":
   > "O VozViva é a API Java do ecossistema Fala Cidadão. A pessoa usa o app falando ou com um toque: pede socorro
   > e a rede de apoio é avisada em segundos, confere se uma mensagem é golpe com IA, e acha o atendimento
   > acessível mais perto, com a rota no celular."
3. **Os recursos do desenho**: apresente cada caixa, dizendo para que serve.
   - "Em azul, o grupo de recursos `rg-vozviva` na Azure, em São Paulo. Tudo dentro dele vai ser criado agora, por
     script."
   - "O **App Service Plan B1** é a máquina que roda as duas APIs: a **API Java**, em container, e a **API .NET**
     de zeladoria."
   - "O **Azure SQL** guarda os dados, com dois bancos: `vozviva` para a API Java e `zeladoria` para a .NET."
   - "O **Container Registry** guarda as imagens da API Java que a pipeline gera."
   - "O **Key Vault** é o cofre das senhas: as APIs leem as senhas dele com a identidade gerenciada, e nenhuma
     senha fica nas configurações nem no código."
   - "O **RabbitMQ** roda num **Container Instance**, dentro de uma **rede virtual** privada, sem endereço
     público."
   - "Um **alerta de gastos** avisa por e-mail antes de os créditos acabarem."
   - "Em laranja, o que fica fora da Azure: o app, a **IA generativa na Oracle** e o **OpenStreetMap**. Em verde,
     o **Azure DevOps**, que leva o código até a nuvem."
4. **Os fluxos**: aponte cada número enquanto fala.
   - **1** "O app chama a API Java por HTTPS, com o token de login."
   - **2** "Cada API grava no seu banco, com um usuário próprio, sem acesso de administrador."
   - **3** "No botão de emergência, a API grava, responde na hora e manda o evento para o RabbitMQ. Em segundo
     plano, os contatos são avisados, com novas tentativas se algo falhar."
   - **4** "A verificação de golpes e a voz usam IA generativa na Oracle, pelo Spring AI, com RAG. Sem IA, regras
     respondem."
   - **5** "O app também usa a API .NET de zeladoria, que aceita o mesmo login."
   - **6** "No Azure DevOps, a tarefa vira branch e pull request; o merge dispara o build com os testes, a imagem
     vai para o registro e a release publica na Azure."
   - **7** "Os locais de atendimento vêm do OpenStreetMap, carregados no banco."
   - **8** "Quando sobem, as APIs buscam as senhas no Key Vault."
   - **Legenda:** "Linha cheia é chamada direta; tracejada é assíncrona; verde é build e deploy; laranja é a IA."

### Parte 2 – Subir os recursos na Azure (≈ 3 min no vídeo final; 20 min gravando)

**Mostrar:** o portal da Azure com o Cloud Shell aberto (o mesmo da etapa A2).

> "Agora vou criar todos esses recursos com um script Azure CLI que está no repositório, na pasta `scripts`."

```bash
git clone -q https://github.com/pedrinzz10/vivavoz.git ~/vivavoz
```
**O que faz:** baixa o projeto para o Cloud Shell. Se a pasta já existir (porque você baixou antes), use
`git -C ~/vivavoz pull -q` para atualizar.

```bash
cd ~/vivavoz
```
**O que faz:** entra na pasta do projeto.

```bash
set -a; . ~/vozviva-segredos.env; set +a
```
**O que faz:** carrega as senhas guardadas na etapa A2 como variáveis de ambiente, **sem mostrar nenhuma na
tela**. O `set -a` faz com que tudo o que é carregado fique disponível para o script.
> "As senhas não ficam no código nem aparecem aqui: o script lê tudo de variáveis de ambiente."

```bash
export SUFIXO=rm562312 LIBERAR_MEU_IP=sim
```
**O que faz:** `SUFIXO` deixa os nomes dos recursos únicos; `LIBERAR_MEU_IP=sim` libera o acesso ao banco, para
os `SELECT` da Parte 4.

```bash
code ~/vivavoz/scripts/provisionar-azure.sh
```
**O que faz:** abre o script no editor do próprio Cloud Shell, em cima do terminal. Assim, enquanto ele roda,
você mostra o código sem sair dessa tela. (Se o editor não abrir, mostre o arquivo numa aba do Azure Repos.)

```bash
./scripts/provisionar-azure.sh 2>&1 | tee ~/provisionar.log
```
**O que faz:** roda o script que cria tudo. O `tee` mostra a saída na tela e guarda uma cópia em
`provisionar.log`, para consultar se algo der errado.
**Tempo:** 15 a 20 minutos. **Fique nesta aba**, rolando o editor e o terminal: o Cloud Shell desconecta depois
de um tempo sem uso.

**Nos primeiros 1 a 2 minutos**, role o script no editor e explique os blocos de uma vez (não precisa esperar cada
um aparecer no terminal). Depois diga:
> "O script leva uns 15 minutos. Vou acelerar o vídeo até ele terminar."

e continue gravando, só rolando o terminal de vez em quando até aparecer `Pronto`. **Na edição, esse trecho é
acelerado.**

O que falar sobre cada bloco do script:

| No terminal aparece | O que falar |
|---|---|
| Grupo de recursos | "Primeiro o grupo `rg-vozviva`, em `brazilsouth`, que reúne tudo." |
| Azure SQL no Chile | "O banco fica em `chilecentral`: em São Paulo a Azure não estava conseguindo criar servidores SQL, e o Chile é a região permitida mais perto, a uns 50 ms das APIs." |
| Key Vault | "Logo depois, o Key Vault: o script grava nele todas as senhas que carreguei. Daqui para a frente, as APIs só recebem uma referência ao cofre." |
| Azure SQL | "O servidor SQL com os bancos `vozviva` e `zeladoria`. A API não usa o administrador: o script cria o usuário `vozviva_app`, que só mexe nos bancos da aplicação." |
| Container Registry | "O registro privado onde a pipeline vai guardar as imagens da API." |
| Rede e RabbitMQ | "A rede virtual e o RabbitMQ em container, só com endereço privado." |
| App Service | "O plano B1 e as duas APIs. Cada API tem uma identidade gerenciada: com ela, a Java baixa a imagem do registro e as duas leem as senhas do Key Vault, só com permissão de leitura. Nas variáveis de ambiente ficam só referências ao cofre." |
| API .NET | "A API .NET de zeladoria é compilada e publicada aqui mesmo." |
| Key Vault: o App Service consegue ler | "O script confere que o App Service conseguiu ler todas as senhas do cofre." |
| Orçamento | "Por fim, um alerta de gastos de 80 dólares, que manda e-mail em 50% e 80% do valor." |

**Resultado esperado:** termina com o título `Pronto` e um resumo com os endereços.

```bash
az resource list -g rg-vozviva --query "[].{nome:name,tipo:type}" -o table
```
**O que faz:** lista tudo o que foi criado no grupo `rg-vozviva`.

**Mostrar no portal:** Resource groups → `rg-vozviva`, com os dois App Services, o plano, o Azure SQL e os bancos,
o Container Registry, o Key Vault, o Container Instance (RabbitMQ) e a rede virtual.
> "Esses são os recursos do desenho, criados pelo script. Ele pode rodar de novo sem duplicar nada."

### Parte 3 – Organização, alteração no código e pipelines (≈ 8 a 9 min no vídeo final)

#### 3.1 Mostrar a organização (≈ 1 min)

Abra `https://dev.azure.com/vozviva/vozviva` e mostre rapidamente:

- **Overview → Summary:** projeto **privado**, processo Scrum, a descrição, as tags e o README do repositório.
- **Overview → Dashboards:** o resumo da solução e o histórico da pipeline.
- **Overview → Wiki:** o repositório publicado como wiki (README, roteiros e arquivos de entrega).
- **Boards → Backlogs:** os itens do backlog.
- **Repos → Files:** o código, a pasta `scripts` (com o `script-bd.sql` e os scripts Azure CLI), a pasta
  `dockerfiles` e o `azure-pipeline.yml` na raiz.
- **Repos → Branches →** `main` → **⋯ → Branch policies:** revisor obrigatório, tarefa vinculada obrigatória e o
  revisor padrão (o seu RM).
> "A main é protegida: só recebe código por pull request, com revisor e tarefa vinculada."

#### 3.2 Ligar o Azure DevOps aos recursos criados (≈ 2 min)

A pipeline precisa de duas "service connections", que são as permissões para o Azure DevOps publicar nos recursos
recém-criados. Vá em **Project settings → Service connections → New service connection**:

1. **Azure Resource Manager** → **Workload identity federation (automatic)**
   - Subscription: `2tdspw-rm562312-pedrooliveira`; Resource group: `rg-vozviva`
   - Nome: **`vozviva-azure`** (exatamente assim: a pipeline procura por esse nome)
   - Marque **Grant access permission to all pipelines** e salve
2. **Docker Registry** → **Azure Container Registry**
   - Subscription: a mesma; registro: `acrvozvivarm562312`
   - Nome: **`vozviva-acr`**
   - Marque **Grant access permission to all pipelines** e salve

> "A conexão com a Azure usa identidade federada: não existe senha guardada no Azure DevOps."

Volte ao Cloud Shell da Azure:

```bash
export ORGANIZACAO=vozviva REVISOR_EMAIL=RM562312@fiap.com.br
```
**O que faz:** informa ao script a organização do Azure DevOps e quem é o revisor obrigatório da main.

```bash
export WEBAPP_NAME=app-vozviva-rm562312
```
**O que faz:** informa o nome do App Service da API Java, para onde a pipeline vai publicar.

```bash
export ACR_LOGIN_SERVER=acrvozvivarm562312.azurecr.io
```
**O que faz:** informa o endereço do registro de imagens, para onde a pipeline vai enviar a imagem.

```bash
./scripts/configurar-azure-devops.sh
```
**O que faz:** confere o projeto, o repositório, a pipeline e as proteções da main (que já existem e são só
reaproveitados) e **cria o grupo de variáveis `vozviva-azure`**, já liberado para a pipeline. As variáveis ficam
no Azure DevOps, nunca no código.

**Mostrar:** Pipelines → **Library** → `vozviva-azure`, com as variáveis `webAppName` e `acrLoginServer`.
> "A pipeline lê esses valores daqui, como variáveis de ambiente."

#### 3.3 Nova tarefa no Boards (≈ 30 s)

**Fazer:** **Boards → Work items → New Work Item** (setinha do botão) **→ Task** → título **"Descrever no Swagger
que o cidadão também mantém o perfil"** → **Save**. **Anote o número da tarefa** que aparece no topo (provavelmente
105).

Não use o "New item" da tela **Boards → Boards**: ali o quadro mostra as Features, e o botão criaria uma Feature, não
uma Task.

#### 3.4 Nova branch e alteração no código-fonte, pelo terminal (≈ 2 min)

1. **Criar a branch pela tarefa** (no navegador): na tarefa aberta, clique no menu **⋯** (canto superior direito) →
   **New branch...** → nome `feature/swagger-perfil`, **Based on** `main` → **Create branch**. A branch já nasce
   ligada à tarefa e aparece na seção **Development** dela.
2. **No Cloud Shell da Azure**, um comando por vez. Troque **105** pelo número da sua tarefa.

```bash
cd ~
```
**O que faz:** volta para a pasta inicial do Cloud Shell.

```bash
R=499b84ac-1321-427f-aa17-267ca6975798
```
**O que faz:** guarda o identificador do Azure DevOps, para o comando seguinte ficar curto.

```bash
T=$(az account get-access-token --resource $R --query accessToken -o tsv)
```
**O que faz:** pega um token de acesso ao Azure DevOps com o login que o Cloud Shell já tem. Não aparece na tela,
e nenhuma senha é digitada.

```bash
H="Authorization: Bearer $T"
```
**O que faz:** monta o cabeçalho de autenticação que o Git vai mandar ao Azure Repos.

```bash
U=https://dev.azure.com/vozviva/vozviva/_git/vozviva
```
**O que faz:** guarda o endereço do repositório no **Azure Repos** (não no GitHub).

```bash
git -c http.extraHeader="$H" clone -q $U ~/repo
```
**O que faz:** baixa o repositório do Azure Repos para a pasta `~/repo`, usando o token.

```bash
cd ~/repo && git switch feature/swagger-perfil
```
**O que faz:** entra no repositório e muda para a branch criada pela tarefa.

```bash
F=src/main/java/br/com/fiap/vozviva/config/OpenApiConfig.java
```
**O que faz:** guarda o caminho do arquivo que vai ser alterado.

```bash
sed -i 's/mantido pelo cuidador/mantido pelo cidadão ou pelo cuidador/' $F
```
**O que faz:** troca o texto da linha 81, a descrição do módulo de perfil no Swagger. Hoje o próprio cidadão também
mantém o perfil, então o texto antigo estava desatualizado.

```bash
git diff
```
**O que faz:** mostra a alteração: a linha antiga em vermelho e a nova em verde. Aperte `q` para sair.
> "A alteração é no código-fonte, não no README."

```bash
git config user.name "Pedro Henrique de Oliveira"
```
```bash
git config user.email RM562312@fiap.com.br
```
**O que faz:** diz ao Git quem é o autor do commit.

```bash
git commit -am "Swagger: o cidadão também mantém o perfil #105"
```
**O que faz:** grava a alteração na branch. O `#105` liga o commit à tarefa no Boards.

```bash
git -c http.extraHeader="$H" push -q
```
**O que faz:** envia o commit para a branch no Azure Repos.

#### 3.5 Pull request, aprovação e merge na main, pelo terminal (≈ 1 min)

```bash
az devops configure --defaults organization=https://dev.azure.com/vozviva
```
```bash
az devops configure --defaults project=vozviva
```
**O que faz:** define a organização e o projeto padrão dos comandos `az repos` a seguir.

```bash
M="Swagger: o cidadão também mantém o perfil"
```
**O que faz:** guarda o título do pull request.

```bash
Q="--query pullRequestId -o tsv"
```
**O que faz:** guarda a parte do comando que pega só o número do PR.

```bash
PR=$(az repos pr create -r vozviva -s feature/swagger-perfil --title "$M" --work-items 105 $Q)
```
**O que faz:** abre o pull request da branch para a `main` (o destino padrão), ligado à tarefa 105. O número do PR
fica guardado em `PR`.

```bash
O="--delete-source-branch true --transition-work-items true"
```
**O que faz:** guarda as opções de conclusão: apagar a branch depois do merge e mover a tarefa para **Done**.

```bash
az repos pr update --id $PR --auto-complete true $O -o none
```
**O que faz:** marca o PR para **concluir sozinho** quando as regras da `main` forem cumpridas, com as opções acima.

```bash
echo "PR $PR: https://dev.azure.com/vozviva/vozviva/_git/vozviva/pullrequest/$PR"
```
**O que faz:** mostra o número e o link do PR. Abra o link no navegador para mostrar o PR no vídeo.

```bash
az repos pr set-vote --id $PR --vote approve -o none
```
**O que faz:** aprova o PR com o seu usuário, que é o revisor obrigatório da `main`. Com a aprovação e a tarefa
vinculada, as regras ficam cumpridas e o PR **conclui sozinho** (merge na `main`).

```bash
az repos pr show --id $PR --query "{status:status, merge:mergeStatus}" -o table
```
**O que faz:** confere o PR. Em alguns segundos, o status passa a `completed`. Se ainda estiver `active`, rode de
novo depois de uns segundos.

> "A main é protegida: o PR só entrou porque tem revisor aprovando e tarefa vinculada. O merge dispara a pipeline
> sozinho, sem ninguém clicar em Run."

**Mostrar no navegador:** o PR concluído (link do `echo`), com a tarefa em **Work items** e o commit.

#### 3.6 Execução completa da pipeline (≈ 2 min no vídeo final; 8 a 12 min gravando)

**Mostrar:** em Pipelines, a execução que acabou de começar (gatilho *Individual CI*, branch `main`). Abra o
estágio de build e, **no primeiro minuto**, explique as tarefas da lista. Depois diga
"Vou acelerar até a pipeline terminar" e deixe gravando, com a tela da execução aberta, até os dois estágios
ficarem verdes. **Na edição, esse trecho é acelerado.** O que falar:

- **Build e testes (CI):** compila com o Gradle, roda centenas de testes (incluindo testes com SQL Server e
  RabbitMQ de verdade), mede a cobertura, gera o pacote, cria a imagem e envia para o registro.
- **Deploy no App Service (CD):** começa sozinho quando a CI termina. Troca a imagem da API Java pela nova e
  confere se a API respondeu "pronta".

Como é a primeira execução com os recursos novos, o Azure DevOps pode mostrar **"This pipeline needs permission to
access a resource"**. Se aparecer, clique em **View** e em **Permit** e explique:
> "Na primeira execução, o Azure DevOps pede uma única autorização para a pipeline usar o ambiente de produção.
> O disparo continua automático."

#### 3.7 Artefatos da CI (≈ 1 min)

**Mostrar,** na execução:

- aba **Tests**: os testes publicados, todos aprovados;
- aba **Code Coverage**: a cobertura dos testes;
- **Related → Published** → artefato **`vozviva`**, com o `vozviva.jar` (a API), o `openapi.json` (contrato da API)
  e o `script-bd.sql` (o banco);
- no portal, o Container Registry → **Repositories** → `vozviva-api`, com a tag do número do build.

#### 3.8 Alteração publicada na nuvem (≈ 30 s)

**Mostrar:** abra `https://app-vozviva-rm562312.azurewebsites.net/swagger-ui.html`. O módulo de perfil diz
**"mantido pelo cidadão ou pelo cuidador"**.
> "A alteração saiu da tarefa e chegou na nuvem passando pelo build, pelos testes e pelo deploy."

### Parte 4 – CRUD em três tabelas, conferindo direto no banco (≈ 4 a 5 min)

Para ser rápido: deixe o Swagger e o Query editor em **duas abas lado a lado** (ou alterne com Ctrl+Tab), cole os
JSON do bloco de notas e, no Query editor, deixe os três `SELECT` escritos e rode só o que interessa (selecione a
linha e clique em **Run**).

Pegue a senha do banco no cofre: portal → Key vaults → `kv-vozviva-rm562312` → **Objetos → Segredos** →
`sql-admin-senha` → a versão atual → clique no ícone de **copiar** ao lado de "Valor do segredo" (não clique em
"Mostrar"). Depois abra o banco: portal → SQL databases → `vozviva` → **Query editor**, entre com o usuário
`vozvivaadmin` e cole a senha (o campo esconde o que é digitado). Se pedir, clique em **Allowlist IP**.
> "A senha do banco também está no Key Vault: copio de lá sem mostrar."

**Regra da disciplina:** para provar que gravou, use **`SELECT` no Query editor**, nunca o GET. Faça o `SELECT`
depois de **cada** operação. Faltar qualquer operação tira 30 pontos.

**4.1 Tabela `usuarios`** (cadastro e login não precisam de token)

| Operação | No Swagger | Corpo da requisição |
|---|---|---|
| **Create** | `POST /api/v1/auth/cadastro` | `{"nome": "Maria da Silva", "email": "maria.video@vozviva.dev", "senha": "girassol2026", "papel": "CIDADAO", "consentimento": true}` |
| (login) | `POST /api/v1/auth/login` | `{"email": "maria.video@vozviva.dev", "senha": "girassol2026"}`. Copie o `accessToken` e cole em **Authorize** |
| **Read** | `GET /api/v1/auth/eu` | — |
| **Update** | `PUT /api/v1/auth/eu` | `{"nome": "Maria da Silva Souza"}` |

```sql
SELECT id, nome, email, papel, consentimento_em FROM usuarios WHERE email = 'maria.video@vozviva.dev';
```
**O que faz:** mostra a linha da Maria na tabela de usuários (rode depois do Create e depois do Update).
O **Delete** desta tabela fica para o fim (4.4), porque apagar a conta apaga também o perfil e os contatos.

**4.2 Tabela `perfis_emergencia`** (com o token da Maria)

| Operação | No Swagger | Corpo da requisição |
|---|---|---|
| **Create** | `POST /api/v1/perfis-emergencia` | `{"mobilidade": "CADEIRANTE", "condicoesSaude": "Hipertensão", "medicamentos": "Losartana 50 mg", "observacoes": "Mora sozinha"}` |
| **Read** | `GET /api/v1/perfis-emergencia/meu` | — (anote o `id` do perfil) |
| **Update** | `PUT /api/v1/perfis-emergencia/{id}` | `{"mobilidade": "ANDADOR", "condicoesSaude": "Hipertensão", "medicamentos": "Losartana 100 mg", "observacoes": "Mora sozinha"}` |

```sql
SELECT id, cidadao_id, mobilidade, medicamentos, atualizado_em FROM perfis_emergencia ORDER BY id DESC;
```
**O que faz:** mostra os perfis, do mais novo para o mais antigo. O primeiro é o da Maria.

**4.3 Tabela `contatos_emergencia`** (use o `id` do perfil no lugar de `{perfilId}`)

| Operação | No Swagger | Corpo da requisição |
|---|---|---|
| **Create** | `POST /api/v1/perfis-emergencia/{perfilId}/contatos` | `{"nome": "Ana Souza", "telefone": "(11) 91234-5678", "parentesco": "Filha", "prioridade": 1}` |
| **Read** | `GET /api/v1/perfis-emergencia/{perfilId}/contatos` | — (anote o `id` do contato) |
| **Update** | `PUT /api/v1/perfis-emergencia/{perfilId}/contatos/{contatoId}` | `{"nome": "Ana Souza", "telefone": "(11) 98888-7777", "parentesco": "Filha", "prioridade": 2}` |
| **Delete** | `DELETE /api/v1/perfis-emergencia/{perfilId}/contatos/{contatoId}` | — |

```sql
SELECT id, perfil_id, nome, telefone, prioridade FROM contatos_emergencia ORDER BY id DESC;
```
**O que faz:** mostra os contatos. Repare que o telefone fica gravado no padrão `+5511912345678`.

**4.4 Os deletes que faltam**

| Operação | No Swagger | Conferência no banco |
|---|---|---|
| **Delete** do perfil | `DELETE /api/v1/perfis-emergencia/{id}` | `SELECT * FROM perfis_emergencia WHERE id = <id>;` → nenhuma linha |
| **Delete** do usuário | `DELETE /api/v1/auth/eu` | `SELECT * FROM usuarios WHERE email = 'maria.video@vozviva.dev';` → nenhuma linha |

> "Cada operação foi conferida direto no Azure SQL. A exclusão da conta atende a LGPD: apaga a conta e tudo o
> que depende dela, e o token antigo para de valer na hora."

### Parte 5 – Tarefa concluída com os links (≈ 30 s)

**Mostrar:** Boards → Work items → a tarefa da etapa 3.3 → estado **Done** → seção **Development**, com a **branch**, o
**commit** e o **pull request**. Se ela não fechou sozinha, mude o estado para **Done** na hora.

> "A tarefa virou branch, commit e pull request, passou pelos testes e foi publicada na Azure automaticamente."

---

## Fase C – Edição: acelerar as esperas

Use o **Clipchamp**, que já vem no Windows 11 (menu Iniciar → Clipchamp), ou outro editor que você já conheça.

1. Importe a gravação e arraste para a linha do tempo.
2. Ache o trecho em que você diz "vou acelerar" no script da Azure. Com a ferramenta **Split** (tesoura), divida
   o vídeo **logo depois dessa frase** e **logo antes de aparecer `Pronto`**.
3. Selecione o pedaço do meio → **Speed** → escolha uma velocidade que deixe o trecho com uns 30 segundos (para
   15 minutos de espera, use 16x ou mais). O áudio desse trecho fica mudo, e tudo bem, porque nele você não fala.
4. Repita o mesmo com a espera da pipeline: do "vou acelerar até a pipeline terminar" até os dois estágios
   ficarem verdes.
5. **Não apague nenhum pedaço.** Só acelere. Confira se o vídeo final ficou com 18 a 20 minutos.
6. Exporte em **1080p** (ou 720p no mínimo) e envie para o YouTube.

## Fase D – Depois de editar

1. **Assista ao vídeo já publicado no YouTube** (público ou não listado): 720p, áudio claro e todas as partes.
2. **Peça para o Claude trazer a alteração do vídeo para o GitHub.** Ela foi feita no Azure Repos e precisa voltar
   para o GitHub.
3. **Monte o PDF** `VozViva_rm562312.pdf` a partir de `docs/entrega/devops-folha-de-rosto.md`: folha de rosto com
   o nome do grupo e o RM e nome completo dos integrantes, o link da organização (`https://dev.azure.com/vozviva`)
   e o link do vídeo.
4. **Mantenha tudo no ar até a correção (30/11).** Depois, para parar o consumo dos créditos:

```bash
./scripts/remover-azure.sh
```
**O que faz:** mostra o que vai ser apagado, pede para você digitar o nome do grupo para confirmar e apaga o
grupo `rg-vozviva` inteiro (com o alerta de gastos). Não tem volta: os dados do banco são perdidos.

---

## Problemas comuns

| O que aconteceu | O que fazer |
|---|---|
| Rodou o `provisionar-azure.sh` logo depois de um `remover-azure.sh` | Espere o grupo sumir de vez antes: `az group exists -n rg-vozviva` tem que dar `false`, e `az sql server list -o table` não pode mostrar o servidor antigo. Criar um servidor SQL enquanto o antigo ainda está sendo apagado trava |
| A criação do servidor SQL passou de 10 minutos | A região está com problema. Interrompa (Ctrl+C), apague com `remover-azure.sh` e rode de novo com outra região só para o banco, por exemplo `export SQL_LOCALIZACAO=mexicocentral` (a assinatura de estudante permite chilecentral, mexicocentral, eastus, eastus2 e brazilsouth) |
| O comando colado deu erro estranho (`command not found`, `expected one argument`) | A linha quebrou ao colar. Cole de novo, um comando por vez, ou digite à mão |
| `Defina SQL_ADMIN_SENHA` (ou outra variável) | As senhas não foram carregadas. Rode de novo o `set -a; . ~/vozviva-segredos.env; set +a`. Se o arquivo sumiu, o Cloud Shell reiniciou: refaça a etapa A2 fora da gravação |
| O Cloud Shell caiu no meio do script | Reabra, recrie o arquivo de senhas com as mesmas senhas do gerenciador e rode o script de novo: ele continua de onde parou. No vídeo, isso exige regravar |
| O `provisionar-azure.sh` parou com erro | Rode `tail -40 ~/provisionar.log` e mande as linhas para o Claude (confira antes que não há senha nelas) |
| A pipeline falhou | Abra o estágio vermelho, copie o erro e mande para o Claude. Depois de corrigir, regrave |
| O Query editor não entra | Clique em **Allowlist IP** quando ele pedir |
| O Swagger publicado não abre logo depois do deploy | A API leva 1 ou 2 minutos para subir. Espere e atualize a página |

## O que tira ponto e onde está coberto

| Item | Desconto | Coberto em |
|---|---|---|
| Código, `azure-pipeline.yml`, scripts, `script-bd.sql`, Dockerfile e pastas `scripts` e `dockerfiles` no repositório | −70 a −10 | Já atendido (mostrado na Parte 3.1) |
| App ou banco em localhost | −70 | Parte 2 |
| Professor sem acesso ao Azure DevOps | −70 | Já atendido (professores convidados) |
| Branch principal desprotegida | −20 | Já atendido (mostrado na Parte 3.1) |
| Sem variáveis de ambiente ou senhas no código | −20 | Partes 2 e 3.2 |
| Sem testes automáticos na CI | −20 | Partes 3.6 e 3.7 |
| Não criou branch para a tarefa | −10 | Parte 3.4 |
| Alteração no README em vez do código | −20 | Parte 3.4 |
| CRUD sem `SELECT` no banco em nuvem | −30 | Parte 4 |
| Vídeo sem narração, abaixo de 720p ou com áudio ruim | −30 | Etapa A4 e Fase C |
| Item obrigatório faltando no vídeo | −20 cada | Partes 1 a 5 |
| Entrega fora do prazo (19/11, 23h55) | −70 | Fase D |
