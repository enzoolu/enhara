# ADR 006 — Simulação stateful de veículo, ECU e OBD

Status: aceito

## Context

Os mocks do CP1 geravam velocidade por senoide e derivavam sinais por fórmulas sem transmissão, capabilities ou estado diagnóstico da ECU. Isso impedia representar troca de marcha, suporte variável e a separação DTC/finding/alert.

## Decision

Usar um modelo determinístico por perfil dentro do módulo `simulator`: driver input, dinâmica longitudinal, motor/transmissão, estado ECU e serviços OBD. Perfis classificam PIDs individualmente. O snapshot OBD expõe capabilities, live data, DTC lifecycle, MIL, freeze frame, readiness e Vehicle Information simulada quando suportada.

O backend mantém o fallback da demo e o mobile usa o mesmo desenho por meio de `VehicleDataSource`. Telemetria simulada tem provenance `SIMULATED_OBD` e nunca contém GPS inventado.

O motor parte próximo da temperatura ambiente e converge gradualmente à faixa operacional; falhas térmicas alteram essa dinâmica, em vez de substituir diretamente o valor do PID. Leituras live permanecem consultáveis após a desconexão, mas passam de `SUPPORTED` para `STALE` depois de cinco segundos sem atualização; capability discovery (`SUPPORTED`, `UNSUPPORTED`, `UNKNOWN`) não é reclassificada por envelhecimento.

Quando a fonte mobile simulada envia um batch, inclui opcionalmente o snapshot OBD produzido pelo mesmo estado causal. O backend torna esse snapshot observável somente depois que o batch é persistido com sucesso; uma falha restaura o estado anterior. O simulador local segue a mesma fronteira: snapshot OBD, contador e eventos SSE só avançam depois do commit da telemetria. O simulador do backend e a fonte mobile não podem publicar estado OBD ao mesmo tempo para o mesmo veículo.

No mobile, coleta/snapshot permanecem na porta veicular genérica; troca de cenário e perfil fica em controles opcionais exclusivos de fontes simuladas. Uma futura fonte Bluetooth não implementa operações fictícias como no-op.

## Consequences

Trocas de marcha e cenários passam a ser testáveis por relações causais. DTCs da ECU ficam observáveis sem serem gravados automaticamente como findings ou alerts. Capabilities, DTCs, MIL, freeze frame e readiness do mobile deixam de seguir um caminho visual paralelo e chegam ao mesmo consumidor web que recebe a telemetria persistida. O snapshot OBD corrente é efêmero; após reinício, o histórico persistido continua disponível, mas a sessão deve redescobrir capabilities.

O modelo existe em Java e TypeScript por causa das duas execuções offline atuais; testes equivalentes cobrem transmissão, aquecimento, disponibilidade, capabilities, cenários e lifecycle, e constantes/comportamento devem permanecer alinhados até haver uma representação compartilhável ou somente uma fonte de simulação.
