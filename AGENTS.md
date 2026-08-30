# Enhara — regras persistentes de engenharia

## Objetivo

Entregar uma plataforma de assistência veicular conectada que converta telemetria técnica em informação preventiva clara. A prioridade do CP1 é uma vertical slice local e demonstrável: ECU simulada/mobile → API Spring Boot → PostgreSQL → diagnóstico → alerta → SSE → dashboard React.

## Stack e arquitetura

- Backend: Java 21, Spring Boot 4 estável, Maven, Spring Web/Data JPA/Validation/Security/Actuator/Modulith, Hibernate, Flyway, PostgreSQL, OpenAPI 3, JUnit 5 e Testcontainers quando Docker estiver disponível.
- Web: React, TypeScript, Vite e EventSource/SSE. TanStack Query, Router, Tailwind, shadcn e Recharts são opcionais quando resolvem necessidade real.
- Mobile: React Native + TypeScript. A coleta deve depender de `VehicleDataSource`, nunca diretamente do mock. Expo é ferramenta de desenvolvimento, não barreira para futura integração nativa Bluetooth/OBD2.
- Arquitetura: modular monolith, package-by-feature, DDD/hexagonal pragmáticos, controllers finos, domínio sem dependência de HTTP ou PostgreSQL.
- Não introduzir microserviços, mensageria, Kubernetes, Redis, MongoDB, GraphQL, CQRS/event sourcing ou ML no MVP.

## Estrutura do monorepo

- `apps/backend`: aplicação Spring Boot.
- `apps/web`: dashboard React.
- `apps/mobile`: aplicativo React Native e simulador ECU desacoplado.
- `packages/api-client` e `packages/shared-types`: contrato TypeScript compartilhado quando viável.
- `contracts/openapi.yaml`: fonte de verdade da API; backend, web e mobile devem manter os mesmos nomes, unidades e tipos.
- `infra/docker`: arquivos auxiliares de containers.
- `docs/architecture`, `docs/adr`, `docs/cp1`, `docs/diagrams`: decisões e material da apresentação.
- `scripts`: automação e plano B da demonstração.

## Módulos backend

Manter fronteiras de `vehicle`, `telemetry`, `diagnostics`, `alerts` e `trips`; `shared/common` deve ser mínimo. Novas classes devem ficar no módulo proprietário (`domain`, `application`, `infrastructure`, `api`) e não em pastas horizontais globais.

## Convenções

- Entidades principais usam UUID; timestamps usam UTC/ISO-8601; números permanecem numéricos; unidades aparecem nos nomes do contrato.
- Schema muda somente por migration Flyway; `ddl-auto=validate` é o padrão.
- Validar ranges e limites de batch; limitar consultas de histórico; indexar `(vehicle_id, recorded_at)`; limpar conexões SSE.
- Diagnósticos usam regras determinísticas centralizadas, linguagem assistiva sem afirmar causalidade não comprovada e deduplicação/cooldown de alertas.
- Seeds são fictícios. Segredos e dados pessoais nunca entram no Git; documentar variáveis em `.env.example`.
- Evitar `any`, magic numbers espalhados, duplicação excessiva e abstrações ritualísticas. Complexidade prematura é proibida; YAGNI prevalece.

## Comandos mínimos

- Backend: `cd apps/backend && ./mvnw test` e `./mvnw package` (`mvnw.cmd` no Windows).
- Web: `cd apps/web && npm ci && npm test -- --run && npm run build` quando testes existirem.
- Mobile: `cd apps/mobile && npm ci && npm run typecheck`; validar bundle com `npx expo export --platform android` quando possível.
- Infra: `docker compose up -d --build`; depois verificar `/actuator/health`, API, persistência, SSE e o cenário OVERHEAT.

## Ordem de prioridade

1. PostgreSQL/Flyway e backend iniciando.
2. Vehicle, telemetria em batch, latest e history.
3. regras ENGINE_TEMPERATURE_HIGH e BATTERY_VOLTAGE_LOW.
4. alertas persistidos, consultáveis, deduplicados e reconhecíveis.
5. SSE e dashboard web com dados reais.
6. mobile com `VehicleDataSource`, mock ECU, START/STOP, NORMAL/OVERHEAT e batching.
7. trips, mapa e polimento somente depois do fluxo acima.

## Definition of done

Código gerado não é funcionalidade pronta. Antes de marcar algo como funcionando, executar build/test proporcional e, para integrações, iniciar serviços e exercitar o fluxo real. O CP1 está pronto quando é possível selecionar o veículo, iniciar simulação NORMAL, observar dados persistidos e atualização do dashboard, mudar para OVERHEAT, gerar alerta automaticamente e vê-lo chegar via SSE sem editar código durante a apresentação.

Se um teste falhar, corrigir a causa; não remover cobertura nem mascarar erro. O contrato OpenAPI deve acompanhar toda mudança de endpoint. Preserve o caminho crítico e não interrompa o desenvolvimento por falhas solucionáveis.
