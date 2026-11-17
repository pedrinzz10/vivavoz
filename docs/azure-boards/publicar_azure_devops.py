"""Publica no Azure DevOps o backlog e o plano de testes de gerar_csv.py (mesma fonte dos CSVs).

Cria (ou reaproveita, se já existir com o mesmo título) Epics > Features > Product Backlog Items, com descrição e
critérios de aceite, e um Test Plan com uma suíte por Feature e os casos de teste (sumário e passos), cada caso
ligado à sua Feature ("Tests"). Pode rodar de novo: só cria o que falta e atualiza os campos do que já existe.

Requer o Azure CLI logado com acesso ao Azure DevOps:
    az login --scope 499b84ac-1321-427f-aa17-267ca6975798/.default

Uso:
    python docs/azure-boards/publicar_azure_devops.py --simular    # só mostra o que faria
    python docs/azure-boards/publicar_azure_devops.py               # publica
Opcionais: --organizacao (padrão vozviva), --projeto (padrão vozviva), --plano (nome do Test Plan).
"""
import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from html import escape

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gerar_csv import CT, EPICOS, lista  # noqa: E402

RECURSO_AZURE_DEVOPS = '499b84ac-1321-427f-aa17-267ca6975798'
API = 'api-version=7.1'


class SemLicencaTestPlans(Exception):
    """Criar Test Plan e suítes exige o acesso "Basic + Test Plans" (ou o trial do Azure Test Plans)."""


class AzureDevOps:
    def __init__(self, organizacao, projeto, simular):
        self.org = f'https://dev.azure.com/{organizacao}'
        self.base = f'{self.org}/{urllib.parse.quote(projeto)}'
        self.projeto = projeto
        self.simular = simular
        self.proximo_id_simulado = -1
        resultado = subprocess.run(
            f'az account get-access-token --resource {RECURSO_AZURE_DEVOPS} --query accessToken -o tsv',
            shell=True, capture_output=True, text=True)
        if resultado.returncode != 0 or not resultado.stdout.strip():
            sys.exit('Não consegui o token do Azure DevOps. Rode: az login --scope '
                     f'{RECURSO_AZURE_DEVOPS}/.default\n{resultado.stderr}')
        self.token = resultado.stdout.strip()

    def chamar(self, metodo, url, corpo=None, tipo='application/json'):
        dados = None if corpo is None else json.dumps(corpo).encode('utf-8')
        pedido = urllib.request.Request(url, data=dados, method=metodo, headers={
            'Authorization': f'Bearer {self.token}', 'Content-Type': tipo, 'Accept': 'application/json'})
        try:
            with urllib.request.urlopen(pedido) as resposta:
                texto = resposta.read().decode('utf-8')
                return json.loads(texto) if texto else None
        except urllib.error.HTTPError as erro:
            detalhe = erro.read().decode('utf-8', 'replace')
            if erro.code == 403 and '/_apis/testplan/' in url:
                raise SemLicencaTestPlans() from erro
            sys.exit(f'\nErro {erro.code} em {metodo} {url}\n{detalhe[:800]}')

    def escrever(self, metodo, url, corpo=None, tipo='application/json'):
        """Chamada que altera algo: no modo de simulação, não é enviada."""
        if self.simular:
            self.proximo_id_simulado -= 1
            return {'id': self.proximo_id_simulado}
        return self.chamar(metodo, url, corpo, tipo)

    # --- work items -------------------------------------------------------------------------------------------

    def buscar(self, tipo, titulo):
        aspas = titulo.replace("'", "''")
        consulta = ("SELECT [System.Id] FROM workitems WHERE [System.TeamProject] = @project "
                    f"AND [System.WorkItemType] = '{tipo}' AND [System.Title] = '{aspas}' "
                    "AND [System.State] <> 'Removed'")
        achados = self.chamar('POST', f'{self.base}/_apis/wit/wiql?{API}', {'query': consulta})['workItems']
        return achados[0]['id'] if achados else None

    def salvar(self, tipo, titulo, campos, ligacoes=()):
        """Cria o work item ou atualiza os campos do que já existe. Devolve (id, criado?)."""
        operacoes = [{'op': 'add', 'path': f'/fields/{campo}', 'value': valor}
                     for campo, valor in {'System.Title': titulo, **campos}.items()]
        existente = self.buscar(tipo, titulo)
        if existente:
            self.escrever('PATCH', f'{self.org}/_apis/wit/workitems/{existente}?{API}', operacoes,
                          'application/json-patch+json')
            return existente, False
        for relacao, alvo in ligacoes:
            operacoes.append({'op': 'add', 'path': '/relations/-', 'value': {
                'rel': relacao, 'url': f'{self.org}/_apis/wit/workItems/{alvo}'}})
        criado = self.escrever('POST', f'{self.base}/_apis/wit/workitems/${urllib.parse.quote(tipo)}?{API}',
                               operacoes, 'application/json-patch+json')
        return criado['id'], True

    # --- plano de testes --------------------------------------------------------------------------------------

    def plano(self, nome):
        planos = self.chamar('GET', f'{self.base}/_apis/testplan/plans?{API}')['value']
        for plano in planos:
            if plano['name'] == nome:
                return plano['id'], plano['rootSuite']['id'], False
        criado = self.escrever('POST', f'{self.base}/_apis/testplan/plans?{API}',
                               {'name': nome, 'areaPath': self.projeto, 'iteration': self.projeto})
        return criado['id'], criado.get('rootSuite', {}).get('id', criado['id']), True

    def suite(self, plano_id, raiz_id, nome):
        if plano_id > 0:
            suites = self.chamar('GET', f'{self.base}/_apis/testplan/Plans/{plano_id}/suites?{API}')['value']
            for suite in suites:
                if suite['name'] == nome:
                    return suite['id'], False
        criada = self.escrever('POST', f'{self.base}/_apis/testplan/Plans/{plano_id}/suites?{API}',
                               {'suiteType': 'staticTestSuite', 'name': nome, 'parentSuite': {'id': raiz_id}})
        return criada['id'], True

    def incluir_casos(self, plano_id, suite_id, ids):
        ja = set()
        if plano_id > 0 and suite_id > 0:
            atuais = self.chamar('GET', f'{self.base}/_apis/testplan/Plans/{plano_id}/Suites/{suite_id}/TestCase?{API}')
            ja = {int(c['workItem']['id']) for c in atuais['value']}
        faltando = [i for i in ids if i not in ja]
        if faltando:
            self.escrever('POST', f'{self.base}/_apis/testplan/Plans/{plano_id}/Suites/{suite_id}/TestCase?{API}',
                          [{'workItem': {'id': i}} for i in faltando])
        return len(faltando)


def passos_xml(passos):
    """Formato do campo Microsoft.VSTS.TCM.Steps: ação do testador e resultado esperado em cada passo."""
    itens = ''.join(
        f'<step id="{n}" type="ValidateStep">'
        f'<parameterizedString isformatted="true">{escape(escape(acao))}</parameterizedString>'
        f'<parameterizedString isformatted="true">{escape(escape(resultado))}</parameterizedString>'
        '<description/></step>'
        for n, (acao, resultado) in enumerate(passos, start=2))
    return f'<steps id="0" last="{len(passos) + 1}">{itens}</steps>'


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--organizacao', default='vozviva')
    parser.add_argument('--projeto', default='vozviva')
    parser.add_argument('--plano', default='Plano de testes VozViva')
    parser.add_argument('--simular', action='store_true', help='mostra o que faria, sem alterar nada')
    args = parser.parse_args()

    ado = AzureDevOps(args.organizacao, args.projeto, args.simular)
    contagem = {'criados': 0, 'atualizados': 0}
    marca = '[simulação] ' if args.simular else ''

    def registrar(tipo, titulo, criado):
        contagem['criados' if criado else 'atualizados'] += 1
        print(f'  {marca}{"criado" if criado else "atualizado"}: {tipo} "{titulo}"')

    print('Backlog')
    ids_feature = {}
    ordem = 0
    for epico, descricao, features in EPICOS:
        ordem += 1000
        criterio_epico = lista([f'Feature "{f[0]}" concluída e aceita' for f in features])
        epico_id, criado = ado.salvar('Epic', epico, {
            'System.Description': descricao, 'Microsoft.VSTS.Common.AcceptanceCriteria': criterio_epico,
            'Microsoft.VSTS.Common.Priority': 1, 'Microsoft.VSTS.Common.BacklogPriority': ordem,
            'System.Tags': 'VozViva'})
        registrar('Epic', epico, criado)
        for feature, desc, criterios, pbis in features:
            ordem += 10
            feature_id, criado = ado.salvar('Feature', feature, {
                'System.Description': desc, 'Microsoft.VSTS.Common.AcceptanceCriteria': lista(criterios),
                'Microsoft.VSTS.Common.Priority': 1, 'Microsoft.VSTS.Common.BacklogPriority': ordem,
                'System.Tags': 'VozViva'}, [('System.LinkTypes.Hierarchy-Reverse', epico_id)])
            ids_feature[feature] = feature_id
            registrar('Feature', feature, criado)
            for titulo, historia, crit in pbis:
                ordem += 1
                _, criado = ado.salvar('Product Backlog Item', titulo, {
                    'System.Description': historia, 'Microsoft.VSTS.Common.AcceptanceCriteria': lista(crit),
                    'Microsoft.VSTS.Common.Priority': 2, 'Microsoft.VSTS.Common.BacklogPriority': ordem,
                    'System.Tags': 'VozViva'}, [('System.LinkTypes.Hierarchy-Reverse', feature_id)])
                registrar('PBI', titulo, criado)

    # Os casos de teste são work items (aparecem no Boards ligados à Feature) e saem antes do plano: criar o
    # Test Plan e as suítes exige a licença de Test Plans, e sem ela os casos já ficam prontos.
    print('\nCasos de teste')
    casos_por_feature = {}
    for feature, casos in CT.items():
        ids_casos = []
        for titulo, tipo, objetivo, preparacao, entradas, esperado, passos in casos:
            sumario = (f'<b>Objetivo:</b> {escape(objetivo)}<br><b>Preparação:</b> {escape(preparacao)}<br>'
                       f'<b>Entradas:</b> {escape(entradas)}<br><b>Saída esperada:</b> {escape(esperado)}')
            nome = f'[{tipo}] {titulo}'
            caso_id, criado = ado.salvar('Test Case', nome, {
                'System.Description': sumario, 'Microsoft.VSTS.TCM.Steps': passos_xml(passos),
                'Microsoft.VSTS.Common.Priority': 2, 'System.Tags': f'VozViva; {tipo}'},
                [('Microsoft.VSTS.Common.TestedBy-Reverse', ids_feature[feature])])
            registrar('Test Case', nome, criado)
            ids_casos.append(caso_id)
        casos_por_feature[feature] = ids_casos
    total_casos = sum(len(ids) for ids in casos_por_feature.values())

    print(f'\nPlano de testes "{args.plano}"')
    plano_id = None
    try:
        plano_id, raiz_id, criado = ado.plano(args.plano)
        print(f'  {marca}{"criado" if criado else "já existia"}: Test Plan')
        for feature, ids_casos in casos_por_feature.items():
            suite_id, criada = ado.suite(plano_id, raiz_id, feature)
            incluidos = ado.incluir_casos(plano_id, suite_id, ids_casos)
            print(f'  {marca}{"criada" if criada else "já existia"}: suíte "{feature}" '
                  f'({incluidos} caso(s) incluído(s))')
    except SemLicencaTestPlans:
        print('  PULADO: a conta não tem acesso para criar Test Plans (403). Ative o trial do Azure Test Plans\n'
              '  (ou o acesso "Basic + Test Plans") e rode este script de novo: só o plano e as suítes faltam.')

    print(f'\n{marca}Pronto: {contagem["criados"]} criados e {contagem["atualizados"]} atualizados '
          f'({len(EPICOS)} epics, {len(ids_feature)} features, {total_casos} casos de teste).')
    print(f'Backlog: {ado.base}/_backlogs/backlog')
    if plano_id:
        print(f'Test Plans: {ado.base}/_testPlans/define?planId={plano_id}')


if __name__ == '__main__':
    main()
