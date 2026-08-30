# Funcionalidades implementadas

## Funcionando e validadas

- Backend Spring Boot inicia, aplica Flyway, expõe Actuator health e processa veículo, telemetria, diagnóstico, alerta, Vehicle Health, trips e SSE.
- Ingestão unitária e em lote, latest e history persistem números e timestamps tipados.
- Regras de temperatura alta, bateria baixa e sobrerrotação; resolução automática, cooldown por alerta aberto e reconhecimento.
- Dashboard obtém snapshot REST, apresenta gráfico/instrumentos, Vehicle Health e trips, recebe SSE e controla os três cenários do simulador.
- O cenário `OVERHEAT` produz alerta crítico no E2E de navegador sem recarregar a página.
- Mobile usa `VehicleDataSource`, leitura local, buffer com reenvio, lotes e trips; typecheck e bundle Android passam.
- Trips explícitas e automáticas pelo simulador persistem resumo e indicadores experimentais de condução.
- OpenAPI, Maven, builds web/mobile, scripts, CI e fallback H2 estão reproduzíveis.

## Implementado, mas não validado neste ambiente

- Subida do PostgreSQL 17 e dos containers via Compose. Docker/PostgreSQL não estavam instalados na máquina de implementação; migration, driver, configuração e health checks estão presentes.

## Parcial

- Spring Security permite explicitamente os endpoints do MVP, mas identidade e autorização de usuário ainda não existem.
- `BluetoothVehicleDataSource` define o limite de integração, mas é um stub explícito até haver hardware real.
- GPS é aceito e persistido quando enviado, sem coleta móvel nativa ou mapa completo.

## Roadmap

GPS/rotas reais, calibração científica das métricas de condução, push, painel de frotas, oficinas, analytics avançado, produção em nuvem e governança LGPD completa não são apresentados como funcionalidades prontas.
