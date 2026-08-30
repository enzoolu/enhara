# Enhara

MVP local de assistência veicular conectada. Uma ECU simulada no aplicativo móvel produz telemetria; a API persiste as leituras, aplica regras determinísticas e transmite diagnósticos, alertas e saúde do veículo via SSE para o dashboard.

![Dashboard Enhara durante o cenário OVERHEAT](docs/cp1/dashboard.png)

## Demo rápida no Windows

Pré-requisitos já instalados: Java 21+ e Node.js/npm. Depois de executar `npm ci` uma vez na raiz:

```powershell
.\reset-demo.cmd
```

Esse único comando zera o H2, inicia backend e web, cria o veículo fictício e deixa o cenário `NORMAL` ativo.

- Dashboard: http://127.0.0.1:5173
- Health: http://127.0.0.1:8080/actuator/health
- Swagger: http://127.0.0.1:8080/swagger-ui.html

Controles disponíveis:

```powershell
.\start-demo.cmd          # inicia sem apagar o estado atual do processo
.\reset-demo.cmd          # reinicia com banco H2 limpo e cenário NORMAL
.\scripts\demo-flow.ps1  # fallback autoassertivo NORMAL -> OVERHEAT
.\stop-demo.cmd           # encerra backend e web; preserva logs em .data/demo
```

O roteiro completo e o troubleshooting estão em [docs/cp1/demo.md](docs/cp1/demo.md).

## Funcionando e validado

- Backend Spring Boot 4: veículos, telemetria unitária/em lote, latest/history, diagnósticos, alertas deduplicados, acknowledge, simulador e SSE.
- Regras explicáveis: `ENGINE_TEMPERATURE_HIGH`, `BATTERY_VOLTAGE_LOW` e `ENGINE_OVERSPEED`, sem inferir causa mecânica.
- Saúde do veículo calculada no backend (`GOOD`, `ATTENTION`, `CRITICAL`, score e explicação observada).
- Trips com início/fim, persistência, distância, velocidades média/máxima e métricas experimentais de condução.
- Dashboard React responsivo com instrumentos, gráfico temporal, conexão/freshness, alertas, saúde e histórico de viagens.
- Mobile Expo com `VehicleDataSource`, Mock ECU gradual, `NORMAL`, `OVERHEAT`, `LOW_BATTERY`, batching, feedback de conexão/envio, saúde e viagens.
- Flyway v1/v2, OpenAPI 3.1, testes JUnit/Vitest/Playwright, Dockerfiles, Compose e CI.
- Fluxo H2 comprovado: `NORMAL -> OVERHEAT -> diagnóstico -> alerta via SSE -> Health crítico -> viagem finalizada`.

A matriz de evidências desta rodada está em [docs/cp1/evidence.md](docs/cp1/evidence.md).

## Em desenvolvimento ou não validado neste ambiente

- O Compose e a configuração PostgreSQL 17 estão implementados, mas Docker e `psql` não estavam disponíveis nesta máquina; PostgreSQL não foi executado nesta rodada.
- A classe `BluetoothVehicleDataSource` mantém o ponto de extensão, porém Bluetooth/ELM327 físico ainda não está integrado.
- As métricas de condução são indicadores determinísticos experimentais do MVP, não medições de precisão científica.
- Autenticação está aberta localmente; Spring Security protege apenas o restante da superfície fora dos endpoints explicitamente públicos do MVP.
- A localização exibida usa coordenadas da telemetria simulada, não GPS físico validado.

## Roadmap

- Smoke test e Testcontainers com PostgreSQL em ambiente Docker.
- OBD-II/Bluetooth real com permissões, reconexão e validação em veículo controlado.
- Autenticação/autorização por proprietário e políticas LGPD.
- GPS real, trajetos em mapa e calibração das métricas de condução com dados controlados.
- Notificações e manutenção preventiva depois da validação do fluxo central.

## Stack e arquitetura

- Java 21, Spring Boot 4, Maven, JPA, Validation, Security, Actuator, Modulith, Flyway, PostgreSQL/H2.
- React, TypeScript, Vite, EventSource/SSE e CSS responsivo.
- React Native/Expo 57 e TypeScript.
- Monólito modular package-by-feature; domínio desacoplado de HTTP e da fonte móvel simulada.

```text
React Native / VehicleDataSource
        │ lotes HTTP
        ▼
Spring Boot ── Flyway/JPA ── PostgreSQL ou H2 demo
        │          ├─ diagnósticos + alertas
        │          ├─ saúde determinística
        │          └─ trips + métricas experimentais
        ▼
       SSE ──────────────────► React Dashboard
```

## PostgreSQL com Docker

Quando Docker estiver disponível:

```bash
docker compose up -d --build
```

O [compose.yaml](compose.yaml) inicia PostgreSQL 17, aplica migrations Flyway, sobe a API e publica o dashboard por Nginx. Variáveis de desenvolvimento estão em [.env.example](.env.example).

## Aplicativo móvel

```powershell
cd apps/mobile
npm ci
$env:EXPO_PUBLIC_API_URL='http://SEU-IP-LAN:8080'
npm start
```

No emulador Android, o padrão é `http://10.0.2.2:8080`; iOS usa `localhost`. Em dispositivo físico, use o IP LAN do computador. Variáveis `EXPO_PUBLIC_*` são públicas no bundle e não devem conter segredos.

## Endpoints principais

- `GET /api/vehicles` e `GET /api/vehicles/{vehicleId}`
- `POST /api/telemetry/batches`
- `GET /api/vehicles/{vehicleId}/telemetry/latest|history`
- `GET /api/vehicles/{vehicleId}/dashboard|health`
- `GET|POST /api/vehicles/{vehicleId}/trips...`
- `GET /api/vehicles/{vehicleId}/diagnostics|alerts|events`
- `PATCH /api/alerts/{alertId}/acknowledge`
- `GET|POST /api/vehicles/{vehicleId}/simulation...`

O contrato completo está em [contracts/openapi.yaml](contracts/openapi.yaml).

## Verificações

```powershell
cd apps/backend
.\mvnw.cmd test
.\mvnw.cmd package

cd ..\web
npm run lint
npm test -- --run
npm run build

cd ..\mobile
npm run typecheck
npx expo export --platform android --output-dir dist

cd ..\..
npm run test:e2e
npx --yes @redocly/cli@2.49.0 lint contracts/openapi.yaml
```

## Estrutura

- `apps/backend` — monólito modular, migrations e testes.
- `apps/web` — dashboard React/Vite.
- `apps/mobile` — Expo/React Native, `VehicleDataSource`, Mock ECU e batching.
- `packages/shared-types` e `packages/api-client` — contrato TypeScript compartilhado.
- `contracts/openapi.yaml` — fonte de verdade da API.
- `docs` — arquitetura, ADRs, roteiro e evidências do CP1.
- `scripts` — automação e fallback da apresentação.
