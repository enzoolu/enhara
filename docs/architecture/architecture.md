# Arquitetura do Enhara CP1

## Contexto e componentes

O CP1 transforma telemetria veicular simulada em diagnóstico preventivo explicável. O app mobile coleta pela porta `VehicleDataSource` e envia lotes REST. O monólito modular Spring Boot valida e persiste amostras, executa regras determinísticas, mantém diagnósticos e alertas e publica mudanças por SSE. PostgreSQL guarda o histórico e o dashboard React combina snapshot REST com atualizações ao vivo.

```mermaid
flowchart TD
  ECU[Veículo / ECU] -->|OBD-II futuro| BT[Bluetooth / ELM327]
  BT --> VDS[VehicleDataSource]
  MOCK[Mock ECU: Normal / Overheat / Low battery] --> VDS
  VDS -->|REST batch| API[Spring Boot modular monolith]
  API --> VEH[Vehicle]
  API --> TEL[Telemetry]
  TEL --> DIAG[Diagnostics]
  DIAG --> ALERT[Alerts]
  API -->|JPA + Flyway| PG[(PostgreSQL)]
  TEL --> SSE[SSE por veículo]
  DIAG --> SSE
  ALERT --> SSE
  SSE --> WEB[React Dashboard]
  WEB -->|REST snapshot, histórico e comandos| API
```

## Responsabilidades e fluxo

- **Mobile/ECU:** produz uma leitura por segundo, mostra o valor localmente e sincroniza a fila em lotes. A tela não depende da implementação mock.
- **Vehicle:** registra identidade e metadados não sensíveis do veículo fictício.
- **Telemetry:** impõe limites de lote e histórico, persiste números com unidades explícitas e coordena o caso de uso principal.
- **Diagnostics:** executa regras centralizadas, ativa condições e as resolve quando as leituras voltam ao normal.
- **Alerts:** abre uma ocorrência enquanto não existir alerta equivalente aberto, permite consulta e reconhecimento.
- **Realtime:** mantém emitters SSE por veículo, publica eventos e remove conexões em completion, timeout ou erro.
- **Dashboard:** carrega snapshot REST, desenha histórico real e aplica eventos incrementais sem polling agressivo.

A persistência usa apenas migrations Flyway e Hibernate com `ddl-auto=validate`. O índice `(vehicle_id, recorded_at)` atende latest e history. A avaliação de amostra, diagnóstico e alerta acontece no mesmo processo para preservar consistência e simplicidade no CP1.

## Por que monólito modular

As features têm fronteiras de domínio visíveis, mas ainda compartilham uma transação e uma equipe. Separá-las em serviços agora adicionaria falhas distribuídas, consistência eventual e operação sem benefício demonstrado.

## Evolução plausível, não implementada

1. Monólito modular e PostgreSQL medidos em produção controlada.
2. Separação lógica de processamentos comprovadamente pesados.
3. Broker de eventos somente se volume e desacoplamento justificarem.
4. Kafka apenas para telemetria sustentada de alta escala.
5. Particionamento ou armazenamento especializado conforme retenção medida.
6. Extração de serviços somente por necessidade operacional ou organizacional concreta.
