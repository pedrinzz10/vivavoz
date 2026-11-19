# Azure Boards e Test Plans

Backlog e casos de teste do VozViva para o Azure DevOps (Compliance, QA & Tests).

| Arquivo | O que é |
|---|---|
| [`backlog.csv`](backlog.csv) | 5 Epics, 9 Features e 23 Product Backlog Items (processo Scrum), para **Boards → Work items → Import Work Items** |
| [`casos-de-teste/`](casos-de-teste) | 49 casos de teste, um CSV por Feature, para **Test Plans → Import test cases** |
| [`gerar_csv.py`](gerar_csv.py) | Gera os CSVs: para mudar um item, edite o script e rode `python docs/azure-boards/gerar_csv.py` |
| [`publicar_azure_devops.py`](publicar_azure_devops.py) | Cria tudo pela API do Azure DevOps, sem passar pelos CSVs (backlog com hierarquia, casos de teste, Test Plan e suítes); `--simular` só mostra o que faria |
