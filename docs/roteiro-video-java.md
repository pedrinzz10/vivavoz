# Roteiro do vídeo pitch (Java Advanced)

Guia para gravar o **vídeo pitch** da entrega de Java Advanced. Ele também conta para a premiação da GS.

| Regra do enunciado | Como fica |
|---|---|
| Até **5 minutos** (o que passar não é avaliado) | Meta de **4 min 40 s**, com folga |
| Link público (YouTube público ou não listado) | Suba como **não listado** e teste o link numa aba anônima |
| Onde entregar | No `.txt` da entrega de Java ([`docs/entrega/java-advanced.txt`](entrega/java-advanced.txt)) e no README |

O pitch é para **quem vai usar e para quem vai pagar**, não uma aula de código. A demonstração mostra a API
publicada de verdade, mas a fala é sobre a pessoa, não sobre a tecnologia.

## Estrutura (a mesma do enunciado)

| Tempo | Bloco | O que aparece na tela |
|---|---|---|
| 0:00 – 0:45 | 1. O problema | README, seção "O problema" (ou um slide com os números) |
| 0:45 – 1:30 | 2. A solução | README, tabela "A solução" e o desenho `docs/arquitetura.svg` |
| 1:30 – 3:00 | 3. Demonstração | Swagger da API publicada: voz → socorro → golpe → hospital acessível |
| 3:00 – 4:00 | 4. Modelo de negócio | README, "Quem paga e como se sustenta" |
| 4:00 – 4:40 | 5. Impacto e fechamento | README, "Como o impacto é medido" e a tabela de ODS |

## Antes de gravar (fora do vídeo, 10 min)

1. **Prepare a cidadã de demonstração.** No Git Bash, na pasta do projeto, ou no Cloud Shell da Azure:

   ```bash
   bash scripts/preparar-pitch.sh
   ```

   O script cria a "Maria da Silva", cadeirante que mora sozinha, com o perfil de emergência e a filha Ana como
   contato, e no fim mostra um **token**. O token vale 2 horas, então rode o script perto da hora de gravar.

2. **Abra o Swagger publicado:** https://app-vozviva-rm562312.azurewebsites.net/swagger-ui.html.
   - Clique em **Authorize**, cole o token e confirme.
   - Abra (expanda) as cinco rotas da demonstração, nesta ordem: `POST /api/v1/voz/intencoes`,
     `POST /api/v1/emergencias`, `GET /api/v1/emergencias/{id}/avisos`, `POST /api/v1/verificacoes` e
     `GET /api/v1/atendimentos/proximos`.
   - Clique em **Try it out** em cada uma, para não perder tempo na gravação.
3. **Deixe num bloco de notas** os textos da demonstração (abaixo), para copiar e colar.
4. **Abas na ordem:** README no GitHub (no topo), `docs/arquitetura.svg`, Swagger.
5. **Zoom do navegador em 125%** e notificações desligadas. Microfone testado: áudio limpo vale mais que edição.

Se o **app mobile** estiver pronto e apontando para a API da Azure, use ele na demonstração no lugar do Swagger (a
mesma sequência: falar o pedido de ajuda, tocar no botão, colar a mensagem do golpe e pedir o hospital). O
enunciado pede "protótipo rodando", e o app mostra isso melhor. O Swagger fica como plano B.

### Textos para o bloco de notas

```json
{ "texto": "caí no banheiro e não consigo levantar", "idioma": "pt-BR" }
```

```json
{ "latitude": -23.52648, "longitude": -46.77665 }
```

```json
{ "texto": "INSS: seu benefício será bloqueado hoje. Para regularizar, informe sua senha e o código que enviamos no link bit.ly/inss-regulariza", "canal": "WHATSAPP" }
```

Atendimento próximo, nos campos da rota: `tipo` = `HOSPITAL`, `latitude` = `-23.52648`,
`longitude` = `-46.77665`, `acessivel` = `true`, `limite` = `1`.

## Gravação: o que falar em cada bloco

As falas são uma sugestão. Fale com as suas palavras, mas mantenha os números e a ordem.

### 1. O problema (0:00 – 0:45)

**Tela:** README, "O problema e quem a solução atende".

> "O Brasil tem 32 milhões de pessoas com 60 anos ou mais e 14 milhões de pessoas com deficiência, segundo o
> Censo 2022. A cidade não acompanha: quase 70% dos moradores das áreas urbanas vivem em ruas sem nenhuma rampa.
> O resultado é o que chamamos de confinamento em casa: a pessoa deixa de ir ao posto de saúde, depende de
> terceiros e, quando cai sozinha em casa, pode ficar horas sem ajuda. E ainda é o alvo preferido dos golpes
> digitais, como o falso INSS."

### 2. A solução (0:45 – 1:30)

**Tela:** tabela "A solução" e, depois, o desenho da arquitetura.

> "O VozViva é a API Java do ecossistema Fala Cidadão. A pessoa usa o celular por voz ou com um toque, sem menus.
> Ela fala o que precisa e o app abre a tela certa. Tem botão de emergência, que avisa a família em segundos com a
> localização e as informações de saúde. Tem um assistente que responde se uma mensagem é golpe. E acha o hospital
> ou a UBS mais perto com acessibilidade, já com a rota no Google Maps."
>
> "Por trás, uma API Spring Boot na Azure, com login por JWT, inteligência artificial com Spring AI e um modelo
> Llama na Oracle Cloud, e o RabbitMQ para o aviso de emergência não depender de nada mais lento."

### 3. Demonstração (1:30 – 3:00)

**Tela:** Swagger publicado (ou o app). Fale enquanto clica, sem esperar em silêncio.

1. **Voz** (`POST /api/v1/voz/intencoes`, cole o primeiro texto, **Execute**):
   > "A Maria é cadeirante e mora sozinha. Ela caiu e fala: 'caí no banheiro e não consigo levantar'. A IA
   > entende que é um pedido de socorro, e o app abre o socorro na hora."

   Aponte na resposta `SOCORRO` e `ACIONAR_SOCORRO`.

2. **Botão de emergência** (`POST /api/v1/emergencias`, cole a localização, **Execute**):
   > "O pedido é aceito na hora, com status 'acionada'. O aviso aos contatos segue pela fila do RabbitMQ, sem a
   > Maria ter que esperar."

   Copie o `id` da resposta, cole em `GET /api/v1/emergencias/{id}/avisos` e clique em **Execute**:
   > "Em um segundo e pouco a filha dela, a Ana, foi avisada, com a localização no mapa, o aviso de que ela usa
   > cadeira de rodas e a orientação de ligar para o SAMU."

3. **Golpe** (`POST /api/v1/verificacoes`, cole a mensagem do INSS, **Execute**):
   > "Chegou uma mensagem do 'INSS' pedindo a senha. A Maria pergunta se é golpe. A resposta é 'provável golpe',
   > explica os sinais, como o pedido de senha e a ameaça de bloqueio, e indica o canal oficial: a Central 135 ou
   > o aplicativo Meu INSS. O canal oficial vem da nossa base, nunca inventado pela IA."

4. **Hospital acessível** (`GET /api/v1/atendimentos/proximos`, preencha os campos, **Execute**):
   > "Depois do susto, ela quer ir ao hospital. A API acha o pronto-socorro com acessibilidade mais perto, a 1,6
   > quilômetro, e já devolve o link da rota."

   Se der tempo, clique no link `googleMaps` da resposta para mostrar a rota aberta.

### 4. Modelo de negócio (3:00 – 4:00)

**Tela:** README, "Quem paga e como se sustenta".

> "Para o cidadão e para o cuidador, o app é gratuito. Quem paga é quem economiza com ele. As prefeituras, por
> assinatura: menos internações por demora no socorro e dados para campanhas contra golpes. Os planos de saúde,
> que oferecem o app aos clientes da terceira idade e gastam menos com internações evitáveis. E dados anônimos,
> sem nenhum dado pessoal, para o planejamento urbano saber onde estão os obstáculos."
>
> "Para escalar, é barato: os locais de atendimento vêm do OpenStreetMap, então uma cidade nova é só importar a
> região, e um golpe novo entra como um documento na base da IA, sem treinar modelo. Se a IA sair do ar, as
> regras de segurança continuam respondendo: o socorro nunca se perde."

### 5. Impacto e fechamento (4:00 – 4:40)

**Tela:** README, "Como o impacto é medido" e a tabela de ODS.

> "O impacto é medido com os dados que a API já grava: o tempo entre o toque e o aviso à família, com meta de
> menos de 30 segundos (na demonstração foi pouco mais de um segundo); as falas entendidas pela voz; e os golpes
> mais comuns na cidade. Atendemos os ODS 3, saúde e bem-estar; 10, redução das desigualdades; e 11, cidades
> sustentáveis."
>
> "O VozViva merece vencer porque devolve autonomia para quem a cidade deixou em casa: com a própria voz, a pessoa
> pede ajuda, se protege de golpes e chega ao atendimento. Somos o grupo VozViva. Obrigado."

## Depois de gravar

1. Corte só o início e o fim. Confira se o vídeo tem **menos de 5 minutos**.
2. Suba no YouTube como **não listado** e abra o link numa aba anônima para testar.
3. Troque `<link do vídeo>` em `docs/entrega/java-advanced.txt` e `_a preencher_` na linha **Vídeo pitch** do
   README pelo link.
4. Na entrega de Java, envie o `.zip` com o `.txt`.
5. Opcional: apague a cidadã de demonstração com `DELETE /api/v1/auth/eu` no Swagger, ainda com o token dela.
