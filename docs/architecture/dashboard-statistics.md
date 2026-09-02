# Painel Principal e Minhas Estatísticas

## Origem dos dados

As duas telas seguem a mesma regra de proveniência:

- **ECU/OBD:** capabilities, live PIDs, readiness, MIL, freeze frame e DTCs. No ambiente local, apenas essa origem pode ser simulada.
- **Enhara derivado:** Vehicle Health, findings, alertas, distância monitorada, velocidade máxima registrada e resumos de viagem.
- **Usuário:** notas e lembretes persistidos. A interface sempre identifica essa origem e nunca cria seeds para preencher a tela.
- **Cadastro:** nome, placa, fabricante, modelo, ano e odômetro cadastrado. Esses campos não são apresentados como leitura da ECU.

`DTC da ECU`, `finding do Enhara` e `alerta` permanecem entidades conceituais distintas e aparecem em seções separadas.

## Estado atual, último válido e capabilities

O Painel Principal mostra “Estado atual” somente enquanto a sessão da ECU está ativa e há live data. Com a sessão parada, os mesmos valores são rotulados como “Último valor válido”, junto do timestamp da leitura; nenhum valor novo é sintetizado para manter o card preenchido.

Cards personalizáveis são persistidos localmente por veículo e só aceitam parâmetros cujo estado de capability seja `SUPPORTED`. O catálogo “Parâmetros disponíveis” aplica:

- `SUPPORTED`: clicável, com valor, unidade, serviço/PID e timestamp quando houver leitura;
- `SUPPORTED_NO_DATA`: clicável, mas explica que ainda não existe leitura válida;
- `STALE`: clicável, preserva o último valor e deixa explícito que a janela de leitura atual expirou;
- `UNKNOWN`: não clicável, pois a descoberta ainda não foi concluída;
- `UNSUPPORTED`: cinza e não clicável.

O estado efetivo `SUPPORTED_NO_DATA` é derivado também pela presença da leitura: declarar suporte sem retornar live data não autoriza a interface a inventar um valor. O VIN do serviço 09 usa `vehicleInformation`, não live PID numérico.

Ao abrir um parâmetro, a interface apresenta sua origem técnica e usa a telemetria persistida para montar o histórico somente quando o campo correspondente faz parte do contrato. PIDs sem série persistida recebem uma indicação de histórico indisponível; a UI não deriva nem inventa pontos.

## Definição das estatísticas

- **Distância registrada pelo Enhara:** soma de `distanceKm` apenas das viagens concluídas e persistidas. Não é o odômetro cadastrado.
- **Velocidade máxima registrada:** maior `speedKph` entre amostras de telemetria persistidas; é nula se não houver leitura.
- **Consumo médio:** nulo com `INSUFFICIENT_DATA` no contrato atual. Snapshots de nível do tanque não bastam para inferir combustível consumido com segurança; o cálculo só poderá ser habilitado com fuel rate ou série validada equivalente e cobertura suficiente.
- **Atividade e gráficos:** usam exclusivamente viagens, notas e DTCs existentes. Séries vazias recebem estado vazio; zero permanece zero e não ganha preenchimento visual artificial.

## Notas e lembretes

Notas pertencem ao veículo e são persistidas em `vehicle_notes` pela migration `V3`. O fluxo suporta criar, editar, concluir, reabrir e excluir. Cada registro contém título, descrição, categoria, vencimento opcional, status e timestamps; `overdue` é derivado no backend para notas abertas vencidas. A interface traduz esse estado persistido para `PENDENTE`, `CONCLUÍDA` ou `ATRASADA`.

## Atualização ao vivo e mobile

Ativação e resolução de findings são publicadas por SSE. O evento `diagnostic-resolved` remove o finding ativo sem refresh e não altera DTCs ou alertas, que mantêm seus próprios ciclos de vida.

No mobile, o snapshot local da `VehicleDataSource` acompanha o batch apenas como estado da ECU que originou as amostras. Capabilities, DTCs, MIL, freeze frame e readiness exibidos são relidos do backend depois da ingestão; assim, nenhuma leitura ainda não persistida cria um caminho visual paralelo.

## Responsividade

Em desktop, a navegação lateral e os grids preservam a hierarquia das referências visuais sem reprodução pixel a pixel. Abaixo de 900 px, a navegação passa para a base; cards e seções empilham progressivamente. O E2E valida Painel e Estatísticas em 390 × 844 px e reprova overflow horizontal.
