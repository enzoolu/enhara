# Funcionalidades implementadas

## Funcionando e validadas

- Backend Spring Boot inicia, aplica Flyway, expõe Actuator health e processa veículo, telemetria, diagnóstico, alerta, Vehicle Health, trips e SSE.
- Ingestão unitária e em lote, latest e history persistem números e timestamps tipados.
- Regras de temperatura alta, bateria baixa e sobrerrotação; resolução automática, cooldown por alerta aberto e reconhecimento.
- Painel Principal obtém snapshot REST/OBD, apresenta apenas cards personalizáveis suportados, conexão, leitura atual/última válida, Health, alertas, findings, viagens e notas persistidas.
- Minhas Estatísticas apresenta distância monitorada, máxima persistida, consumo explicitamente indisponível sem dados suficientes, DTCs reais da ECU simulada, atividade, notas, readiness e catálogo capability-aware.
- `SUPPORTED` abre detalhes; `UNSUPPORTED` e `UNKNOWN` não são clicáveis; `SUPPORTED_NO_DATA` explica a ausência de leitura. Nenhum gráfico recebe pontos artificiais.
- O cenário `OVERHEAT` produz alerta crítico no E2E de navegador sem recarregar a página.
- Mobile usa `VehicleDataSource`, leitura local, buffer com reenvio, lotes e trips; a fonte simulada delega ao modelo stateful ECU/OBD.
- O simulador backend/mobile relaciona throttle, brake, dinâmica, marcha, RPM, carga, térmica, elétrica e combustível; capabilities por perfil controlam os live PIDs.
- O snapshot OBD simulado expõe DTC pending/confirmed/permanent, MIL, freeze frame, readiness e Vehicle Information sem convertê-los automaticamente em findings ou alerts.
- Trips explícitas e automáticas pelo simulador persistem resumo e indicadores experimentais de condução.
- OpenAPI, Maven, builds web/mobile, scripts, CI e fallback H2 estão reproduzíveis.
- Migration Flyway V3 e endpoints de notas suportam criar, editar, concluir, reabrir e excluir conteúdo fornecido pelo usuário; o endpoint de estatísticas deriva agregados persistidos.
- Meu Carro mantém perfil por campo com provenance, revisão/confirmação manual, VIN opcional e entrada OBD restrita a fonte real.
- BrasilAPI/FIPE oferece catálogo guiado e consulta por código; NHTSA vPIC decodifica VIN por provider desacoplado e conflitos de identidade exigem revisão.
- Cache e status dos providers são persistidos; dados anteriores mantêm a tela operacional offline sem bloquear telemetria, alertas ou SSE.
- Fotos JPEG/PNG e notas são `USER_PROVIDED`, persistentes em reinicializações normais e não usam mocks. Especificações ausentes não são estimadas.
- Migration Flyway V4, endpoints de profile/enrichment/catálogo/fotos e tela web Meu Carro foram validados por integração e E2E.

## Implementado, mas não validado neste ambiente

- Subida do PostgreSQL 17 e dos containers via Compose. Docker/PostgreSQL não estavam instalados na máquina de implementação; migration, driver, configuração e health checks estão presentes.

## Parcial

- Spring Security permite explicitamente os endpoints do MVP, mas identidade e autorização de usuário ainda não existem.
- `BluetoothVehicleDataSource` define o limite de integração, mas é um stub explícito até haver hardware real.
- GPS é aceito e persistido quando enviado, sem coleta móvel nativa ou mapa completo.

## Roadmap

GPS/rotas reais, calibração científica das métricas de condução, push, painel de frotas, oficinas, analytics avançado, produção em nuvem e governança LGPD completa não são apresentados como funcionalidades prontas.
