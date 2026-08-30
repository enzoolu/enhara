# Cobertura dos critérios de aceite

## Entregue e verificado

- Monorepo com backend, web, mobile, contrato e pacotes compartilhados.
- Flyway cria veículos, amostras, diagnósticos e alertas com índices.
- CRUD mínimo de veículo; ingestão unitária/lote; latest/history.
- Regras `ENGINE_TEMPERATURE_HIGH`, `BATTERY_VOLTAGE_LOW` e `ENGINE_OVERSPEED` testadas.
- Diagnóstico resolve com leitura normal; alerta aberto é deduplicado e pode ser reconhecido.
- SSE por veículo com cleanup e heartbeat.
- Dashboard responsivo com snapshot REST, atualizações SSE, estados vazios/erro e três cenários.
- Mobile com `VehicleDataSource`, mock gradual, START/STOP, três cenários, leitura local e batching.
- Backend: 6 testes verdes. Web: 4 testes unitários e 1 E2E verde. Build web, typecheck e export Android mobile verdes.
- OpenAPI 3.1 validado pelo Redocly; Dockerfiles, Compose e CI presentes.

## Parcial por ambiente

- PostgreSQL/Testcontainers: código, migration, driver e Compose estão prontos, mas a máquina de implementação não possuía Docker/PostgreSQL para executar o container. O fluxo real foi exercitado no perfil H2 `demo`.

## Fora do CP1

- Bluetooth/ELM327 real, autenticação de usuários, trips/mapa completo, notificações push e publicação em lojas.
- Observabilidade externa, deploy de nuvem e testes de carga.

## Próximos passos recomendados

1. Rodar CI e smoke test com PostgreSQL em uma máquina Docker.
2. Adicionar autenticação/escopo por usuário antes de expor a API.
3. Prototipar ELM327 com dispositivo real e permissions nativas.
4. Definir retenção/particionamento de telemetria a partir de medições reais de volume.
