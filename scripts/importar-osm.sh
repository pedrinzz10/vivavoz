#!/usr/bin/env sh
# SPEC-007: importa do OpenStreetMap os locais de atendimento de Osasco/SP e arredores e regenera
# src/main/resources/db/migration/R__carga_pontos_atendimento.sql e scripts/script-bd.sql.
# Uso: ./scripts/importar-osm.sh [-Pentrada=resposta-overpass.json]
# Dados © colaboradores do OpenStreetMap, licença ODbL.
set -eu
cd "$(dirname "$0")/.."
./gradlew importarOsm "$@"
