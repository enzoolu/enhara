# Enhara — handoff operacional

Atualizado em 01/09/2026 após a refatoração exclusivamente visual. A seção `REFATORAÇÃO VISUAL — FECHAMENTO` é a fonte de verdade visual atual; as seções seguintes permanecem como histórico funcional.

## REFATORAÇÃO VISUAL — FECHAMENTO

### Telas e componentes remodelados

- Web: Painel principal, Minhas estatísticas e Meu carro, incluindo sidebar, topbar, cards de telemetria e saúde, alertas, findings, viagens, notas, simulador, capabilities, DTCs, readiness, perfil/provenance, FIPE/providers, fotos, estados vazios, loading e erro.
- Mobile React Native: tela completa de telemetria/simulação, health, capabilities, viagens, alertas, DTCs, findings, readiness, feedback de sincronização e estados vazios/erro.
- Responsividade web revisada em desktop e `390 × 844`, com sidebar desktop e navegação inferior mobile sem overflow horizontal.

### Design system aplicado

- Inter local em pesos 400/500/600/700, grid base de 4 px, hierarquia tipográfica compacta e títulos de alto contraste.
- Workspace `#f2f7f5`, cards brancos, texto `#07100e`, neutros verde-acinzentados, acento lilás `#c78cff`, sucesso verde, atenção âmbar e crítico coral.
- Bordas discretas, radii de 8/10/12/18 px, pills completas, sombras suaves, motion curto e suporte a `prefers-reduced-motion`.
- Gráficos, chips, inputs, botões, badges e estados semânticos foram integrados à mesma linguagem clara, sem copiar as referências pixel a pixel.
- Tokens web centralizados em `apps/web/src/styles.css`; tokens React Native centralizados em `apps/mobile/App.tsx`. As fontes do pacote SkillUI são empacotadas localmente, sem download em runtime.

### Arquivos visuais modificados

- `apps/web/src/styles.css`: design system e responsividade completos.
- `apps/web/src/App.tsx`: somente a paleta visual do preenchimento do gráfico.
- `apps/mobile/App.tsx`: tokens, tipografia e StyleSheet; carregamento local da Inter.
- `apps/mobile/package.json` e `apps/mobile/package-lock.json`: dependência visual `expo-font`.
- `docs/cp1/dashboard.png`, `docs/cp1/statistics.png` e `docs/cp1/my-car.png`: evidências visuais regeneradas pelo E2E existente.

### Preservação funcional

- Nenhuma regra de negócio, chamada HTTP, SSE, endpoint, DTO, contrato, backend, domínio, persistência, simulador ou fonte veicular foi alterada nesta rodada.
- O manifesto SHA-256 agregado de 123 arquivos sensíveis (`apps/backend`, `contracts`, `packages`, APIs/tipos/OBD/Health web e `apps/mobile/src`) permaneceu idêntico antes e depois: `6D9059DF1BB194E25E77BD6E1D73BEE7DE82BF23B53D26071AC706F636951C95`.

### Testes executados

- Web: `npm run lint`, Vitest `13/13`, `npm run build` e Playwright `3/3` aprovados.
- Mobile: `npm run typecheck`, simulador `6/6` e `npx expo export --platform android` aprovados; bundle Hermes com 598 módulos e aproximadamente 1,5 MB.
- Inspeção visual real: dashboard desktop e mobile, além das capturas completas de Painel, Estatísticas e Meu carro geradas pelo fluxo E2E.
- `git diff --check` aprovado; permanecem somente avisos conhecidos de conversão LF/CRLF.

### Limitações visuais restantes

- O mobile nativo foi validado por typecheck, testes e export Android, mas não houve inspeção em aparelho/emulador físico nesta rodada.
- O caminho informado `docs/design-reference/skillui/` não existe; a saída SkillUI encontrada e lida integralmente está em `docs/skillui/enhara-design/`.

## FECHAMENTO DO PROMPT 5C

### Estado reconstruído e trabalho já feito pelo agente anterior

- O Prompt 5B estava concluído e validado. Antes da interrupção do 5C, o agente anterior já havia corrigido a publicação de SSE antes do commit, fazendo telemetria e trips usarem `SseHub.publishAfterCommit`.
- Também havia protegido o snapshot OBD recebido no batch mobile contra falha de ingestão, alinhado `Diagnostic.code` ao `varchar(64)` da migration v1 e criado testes para rollback do snapshot externo e publicação SSE transacional.
- Os relatórios Surefire confirmaram uma execução focada de 13 testes aprovada após essas alterações. Ainda não existia execução completa do backend posterior a elas, nem registro do 5C neste handoff.

### Problemas encontrados e correções concluídas agora

- O simulador local ainda tornava o snapshot OBD e seu contador visíveis antes da confirmação da persistência. `SimulationService` agora mantém um snapshot publicado separado, segura o boundary por veículo durante a ingestão e só avança estado observável/contador após sucesso. Teste novo prova que uma falha preserva o último snapshot persistido.
- O STOP do mobile aguardava no máximo um batch e podia finalizar a trip deixando amostras em fila ou uma sincronização concorrente em andamento. O app agora serializa o flush, drena todos os lotes antes de encerrar a trip e, se a API falhar, preserva a fila e mantém a trip aberta com feedback explícito.
- O teste de resumo de trip permanecia intermitente: timestamps Java em nanossegundos podiam ser arredondados pelo banco abaixo do limite inclusivo da consulta. Início/fim de trip agora são normalizados para microssegundos, precisão compatível com PostgreSQL, sem afrouxar a asserção.
- Textos de Health/alert tratavam a tensão do módulo de controle como medição direta da bateria e chamavam findings internos de diagnósticos. Os rótulos agora dizem “tensão elétrica/do módulo” e “findings”; o identificador legado `BATTERY_VOLTAGE_LOW` foi preservado por contrato.
- O OpenAPI não documentava o `409` do batch concorrente com o simulador nem o `400` de cenário/perfil inválido. As respostas foram adicionadas.
- As evidências e o ADR 006 foram atualizados para refletir publicação somente após commit, drenagem da fila mobile e contagens/resultados atuais.

### Arquivos principais alterados no fechamento

- `SimulationService.java`, `SimulationServiceTest.java`, `SseHub.java`, `SseHubTest.java`, `TelemetryService.java`, `TripService.java` e `Diagnostic.java`.
- `apps/mobile/App.tsx`, `VehicleHealthService.java`, `BatteryVoltageLowRule.java` e `contracts/openapi.yaml`.
- `docs/adr/006-stateful-obd-simulation.md`, `docs/architecture/architecture.md`, `docs/cp1/acceptance.md` e `docs/cp1/evidence.md`.

### Validação final

- Backend: `mvnw.cmd test` e `mvnw.cmd package` aprovados; 37/37 testes, 0 falhas, 0 erros. O teste de boundary temporal da trip também passou em três execuções isoladas consecutivas.
- Web: lint sem warnings, Vitest 13/13 e build Vite aprovados.
- Mobile: typecheck, simulador 6/6 e export Android aprovados; bundle Hermes com 583 módulos, aproximadamente 1,5 MB.
- OpenAPI: contrato 3.1 válido no Redocly 2.49.0; permanece somente a advertência estilística conhecida por ausência de resposta 4XX no `GET /api/vehicles`.
- Playwright: 3/3 aprovados em fluxo ao vivo/SSE, separação DTC/finding/alert, capabilities, histórico/notas, Meu Carro persistente e viewport móvel.
- Demo limpa: NORMAL em 43,6 °C/13,4 V; OVERHEAT gerou `ENGINE_TEMPERATURE_HIGH`, alerta `ENGINE_OVERHEAT / CRITICAL`, Health 5/100 e trip 0,23 km. Alerta em 14,2 s/21 ticks; `DEMO FALLBACK APROVADA`.
- `git diff --check` passou, restando apenas avisos de conversão LF/CRLF. Serviços encerrados e portas 8080/5173 livres.

### Funcionalidades validadas

- ECU/OBD causal sem senoides ou aleatoriedade como dinâmica principal; throttle, brake, velocidade, RPM e marcha mantêm relação e troca/downshift/idle têm cobertura.
- Capabilities controlam live PIDs e os estados `SUPPORTED`, `SUPPORTED_NO_DATA`, `UNSUPPORTED`, `UNKNOWN` e `STALE`; DTC pending/confirmed/permanent, MIL, freeze frame, readiness e Vehicle Information permanecem dados da ECU simulada.
- Telemetria, persistência, findings determinísticos, alerts deduplicados, resolução e SSE respeitam a fronteira transacional e mantêm DTC da ECU, finding do Enhara e alert como conceitos distintos.
- Painel, Estatísticas e mobile usam dados persistidos/derivados, origem, timestamp, estados vazios e capabilities. Meu Carro usa cadastro/provenance/providers/cache/fallback e mostra indisponível em vez de inventar dados.

### Limitações restantes

- PostgreSQL/Compose não foi executado porque Docker/`psql` não estão disponíveis neste host; H2 `demo` + Flyway + `ddl-auto=validate` foi o ambiente integrado exercitado.
- Bluetooth/ELM327 físico continua não integrado, como já previsto; apenas a porta/adaptador futuro existe.
- Não houve inspeção em aparelho/emulador mobile físico nesta rodada; typecheck, testes do simulador e export Android passaram.
- `docs/design-reference/` não existe no worktree; as capturas em `docs/cp1/` e a especificação DOCX foram as referências disponíveis.

### Estado do Git e próxima etapa

- Branch `master`, último commit `c6483d7 chore: harden and document cp1 demo`. Nada staged. O worktree acumulado foi preservado: 50 arquivos rastreados modificados e 45 entradas não rastreadas; `git diff --stat` dos rastreados mostra 50 arquivos, 2469 inserções e 631 remoções (arquivos novos não entram nesse total).
- Próxima etapa recomendada: revisar a divisão lógica do diff acumulado e criar commits focados. Em seguida, executar o smoke test PostgreSQL/Compose assim que houver um host com Docker; não há outra correção funcional conhecida dentro do Prompt 5C.

## ESTADO NO MOMENTO DA PARADA — histórico da interrupção

### O que estava sendo feito

- Consolidação das interfaces web e mobile usando a especificação funcional e as capturas existentes, sem refazer a ECU/OBD stateful.
- A rodada estava na etapa final de validação: o E2E completo acabara de passar e faltavam reexecutar a suíte/package do backend após o último ajuste determinístico de teste, repetir o build web final e atualizar a documentação permanente.
- `docs/design-reference/` não existe no worktree atual. Foram usadas a especificação DOCX já reconstruída e as capturas reais em `docs/cp1/` como referências disponíveis.

### Concluído nesta rodada

- Painel e Estatísticas mantêm telemetria atual versus último valor válido, timestamp, cards limitados a capabilities `SUPPORTED`, estados `SUPPORTED_NO_DATA`/`STALE`/`UNKNOWN`/`UNSUPPORTED` e dados persistidos.
- Detalhes de parâmetros suportados agora apresentam origem, timestamp e histórico persistido quando o campo existe na telemetria; parâmetros sem série histórica informam a indisponibilidade sem inventar pontos.
- DTCs exibem código, presença/memória, status, primeira e última ocorrência, MIL, freeze frame, evidência técnica e explicação amigável separada.
- Notas persistidas exibem os estados de produto `PENDENTE`, `CONCLUÍDA` e `ATRASADA`; o E2E exercitou criar, concluir, reabrir, editar e excluir.
- Meu Carro passou a explicitar a última atualização externa, preservando provenance, cache e campos indisponíveis sem dados simulados.
- O web passou a consumir o evento SSE `diagnostic-resolved`, removendo finding resolvido sem refresh. DTC, finding e alert continuam separados.
- O mobile deixou de mostrar snapshot OBD local antes da ingestão: capabilities, DTC, MIL e readiness são relidos do backend após persistência; seus cards são filtrados pelo suporte declarado e diferenciam leitura atual de último valor válido.
- Responsividade web foi validada em `390 × 844`, sem overflow; touch target da navegação inferior foi ampliado. A inspeção visual corrigiu também o card de DTC que ficava estreito em largura intermediária.
- Serviços locais foram encerrados; não há listener nas portas `8080` e `5173`.

### Parcial

- A última alteração em `TelemetryServiceIntegrationTest` tornou os timestamps da viagem determinísticos, mas a suíte backend não foi reexecutada depois dessa alteração por causa da ordem de parada.
- O build web passou antes do ajuste CSS final do card de DTC; esse CSS foi exercitado pelo Vite/E2E, mas o comando de build final ainda deve ser repetido.
- O mobile foi typechecked, testado e exportado para Android, mas não houve inspeção em dispositivo/emulador nesta rodada.
- A documentação permanente não recebeu as decisões desta rodada; por solicitação de parada, somente este handoff foi atualizado.

### O que ainda falta

- Reexecutar backend completo e package no estado atual.
- Reexecutar lint/test/build web no estado final e, se desejado, o E2E 3/3 como confirmação única.
- Revisar as capturas finais e atualizar apenas a documentação permanente relevante para histórico de parâmetro, resolução SSE e leitura OBD mobile pós-persistência.
- Executar `git diff --check`, revisar o grande diff acumulado e organizar commits.
- PostgreSQL/Compose continua pendente neste host sem Docker; Bluetooth/ELM327 físico permanece fora do escopo executável atual.

### Arquivos principais alterados nesta rodada

- `apps/web/src/App.tsx`, `CapabilityPanel.tsx`, `NotesPanel.tsx`, `VehicleProfilePage.tsx`, `obd.ts`, `obd.test.ts` e `styles.css`.
- `apps/mobile/App.tsx` e `apps/mobile/src/api.ts`.
- `apps/backend/src/main/java/br/com/enhara/api/diagnostics/application/DiagnosticRulesService.java` e `apps/backend/src/main/java/br/com/enhara/api/telemetry/application/TelemetryService.java`.
- `apps/backend/src/test/java/br/com/enhara/api/service/TelemetryServiceIntegrationTest.java`.
- `tests/e2e/dashboard.spec.ts` e capturas `docs/cp1/dashboard.png`, `statistics.png` e `my-car.png`.
- `AGENT_HANDOFF.md` é a única alteração feita após a ordem de parada.

### Testes e builds executados

- Web lint: passou sem warnings.
- Web Vitest: `13/13` passaram.
- Web build Vite: passou antes do último ajuste CSS; Vite dev compilou e serviu o CSS final durante o E2E.
- Mobile typecheck: passou.
- Mobile simulador: `6/6` passaram.
- Mobile Expo export Android: passou, 583 módulos e bundle Hermes de aproximadamente 1,5 MB.
- Playwright final: `3/3` passaram. Cobriu fluxo ao vivo/SSE, resolução de finding sem refresh, DTC detalhado, histórico de capability, ciclo completo de notas, responsividade e Meu Carro persistente sem simulação cadastral.
- Backend: compilação inicial acusou um parêntese excedente na nova resolução SSE e foi corrigida. As duas execuções seguintes terminaram em `33/34`: apenas `tripStartAndFinishPersistSummary` falhou por usar timestamps alguns milissegundos à frente do encerramento (`0,0` e depois `20,0` em vez de `32,5`). O teste foi ajustado para usar exatamente `trip.startedAt`, mas ainda não foi reexecutado. O package atual não foi executado.

### Erros e limitações atuais

- Não há erro conhecido no E2E da interface; o único resultado vermelho pendente é a revalidação do teste temporal de viagem descrito acima.
- `docs/design-reference/` está ausente.
- Docker e `psql` não estão disponíveis neste host; PostgreSQL real não foi exercitado.
- O worktree acumula alterações de várias rodadas e ainda não possui commit.
- Há avisos conhecidos de CRLF no Git e de H2 mais novo que a versão validada pelo Flyway; não bloquearam a execução.

### Próxima ação exata

1. Executar `cd apps/backend && .\mvnw.cmd test`; se passar, executar `.\mvnw.cmd package`.
2. Executar `cd apps/web && npm run lint && npm test -- --run && npm run build`.
3. Executar `git diff --check`, revisar as capturas e atualizar a documentação permanente mínima antes de preparar commits.

### Git no momento da parada

- Branch atual: `master`.
- Último commit: `c6483d7 chore: harden and document cp1 demo`.
- `git status --short`: 45 arquivos rastreados modificados, 44 entradas não rastreadas e nenhum arquivo staged. As entradas abrangem backend, web, mobile, contratos, scripts, documentação e os arquivos novos dos módulos notes/statistics/profile/simulator já acumulados nas rodadas anteriores.
- `git diff --stat`: `45 files changed, 2340 insertions(+), 588 deletions(-)`; binário `docs/cp1/dashboard.png` alterado. Arquivos não rastreados não entram nesse total.

## FECHAMENTO DO PROMPT 5B

- As pendências do checkpoint de interrupção foram concluídas sem reabrir a implementação ECU/OBD.
- O ajuste temporal de viagem foi revalidado: backend `34/34` verde e JAR executável empacotado.
- Web final: lint sem warnings, Vitest `13/13` e build Vite aprovados.
- Mobile final: typecheck, simulador `6/6` e export Android Hermes aprovados.
- Integração/inspeção final: Playwright `3/3` aprovados em Painel, Estatísticas, capabilities, notas, Meu Carro, SSE e viewport `390 × 844`; capturas desktop inspecionadas sem nova falha visual.
- `git diff --check` passou; restam somente avisos de conversão LF/CRLF.
- Documentação permanente atualizada apenas com histórico de parâmetro, estado `STALE`, rótulos de notas, resolução SSE e leitura OBD mobile pós-persistência.
- Não há pendência funcional conhecida dentro do 5B. Fora do escopo executável: PostgreSQL/Compose sem Docker e Bluetooth/ELM327 físico.
- Serviços de validação encerrados; portas `8080` e `5173` livres.
- Git permanece na branch `master`, último commit `c6483d7 chore: harden and document cp1 demo`, com 45 arquivos rastreados modificados, 44 entradas não rastreadas e nada staged. `git diff --stat`: 45 arquivos, 2340 inserções e 588 remoções; arquivos novos não entram nesse total.

### Próxima ação após o 5B

Revisar e organizar o diff acumulado para commits. Não iniciar o Prompt 5C sem solicitação explícita.

## Checkpoints

### CHECKPOINT 1 — estado inicial preservado

- Objetivo atual: consolidar a implementação completa sem reabrir partes corretas nem perder o estado acumulado.
- Branch `master`, sincronizada com `origin/master`; último commit `c6483d7 chore: harden and document cp1 demo`.
- Worktree já estava extensamente alterado e sem commit no início desta rodada: 38 arquivos rastreados no `git diff --stat` (`2082 insertions`, `553 deletions`) e vários arquivos novos dos Blocos 2–4. Nada foi descartado.
- Bloco 1 concluído: vertical slice Vehicle → telemetria unitária/batch → PostgreSQL/H2 + Flyway → findings determinísticos → alertas deduplicados → SSE → dashboard; Health e trips também estão integrados.
- Bloco 2 concluído: simulador ECU/OBD stateful e causal em Java/TypeScript, perfis/capabilities, transmissão, DTC lifecycle, MIL, freeze frame e readiness; apenas ECU/OBD é simulada.
- Bloco 3 concluído: Painel Principal, Minhas Estatísticas, parâmetros capability-aware, histórico, notas persistidas e responsividade desktop/mobile web.
- Bloco 4 concluído: Meu Carro sem dados simulados, cadastro manual, provenance, VIN OBD real, BrasilAPI/FIPE, NHTSA vPIC, cache/fallback offline e fotos persistidas.
- Parcial/fora dos Blocos 1–4: adaptador Bluetooth/ELM327 físico é stub explícito; autenticação/LGPD, GPS/mapas e calibração científica não foram iniciados; PostgreSQL/Compose está implementado mas não foi executado porque Docker não existe nesta máquina; o nome legado `Diagnostic` ainda representa finding no backend.
- Problemas conhecidos no início: nenhuma falha funcional conhecida; OpenAPI possui uma advertência estilística preexistente no `GET /api/vehicles`; duplicação intencional do simulador Java/TypeScript exige testes equivalentes; worktree grande aumenta o risco de perda se não for preservado.
- Próximos passos desta rodada: auditar somente os pontos críticos de integração e possíveis caminhos paralelos; validar ECU/backend; registrar CHECKPOINT 2; validar web/mobile e responsividade; registrar CHECKPOINT 3; executar builds/testes/E2E e registrar CHECKPOINT 4.

### CHECKPOINT 2 — ECU e backend consolidados

- A busca focal não encontrou `random`/senoide nem dados de UI falsos no simulador. Java e TypeScript continuam derivados do mesmo estado causal.
- A cobertura agora valida também brake → desaceleração → repouso em primeira → idle de 800 rpm, carga plausível e readiness completo, além da troca ascendente já coberta (RPM cai, velocidade não cai e RPM volta a subir).
- Testes focados backend passaram: 14/14 (`StatefulVehicleSimulatorTest`, `SimulationServiceIntegrationTest`, `TelemetryServiceIntegrationTest`). Isso cobre transmissão, capabilities, cenários, DTC lifecycle, separação DTC/finding/alert e ingestão/persistência.
- Testes equivalentes do simulador mobile passaram: 5/5; `npm run typecheck` também passou.
- Foi encontrada e corrigida uma inconsistência de integração mobile: o app injetava uma leitura local com ID sintético dentro do `DashboardData` antes da persistência. Agora a telemetria principal só é atualizada após batch aceito + novo snapshot do backend.
- A leitura OBD local ficou explicitamente separada e mostra capabilities, DTCs/MIL/freeze frame e readiness; findings do backend e alerts permanecem seções distintas.
- Nenhuma regra backend, endpoint ou contrato precisou mudar nesta etapa.
- Próxima etapa: validar web e mobile, responsividade e build/export; corrigir somente falha concreta e registrar CHECKPOINT 3.

### CHECKPOINT 3 — web e mobile consolidados

- Web permaneceu coerente com os Blocos 3–4: lint passou sem warnings, 11/11 testes Vitest passaram e o build Vite foi gerado.
- O E2E existente cobre navegação desktop, viewport 390 × 844 sem overflow e Meu Carro; sua reexecução integra a etapa final de testes (CHECKPOINT 4).
- Mobile passou novamente no typecheck após a correção de fluxo e gerou export Android Hermes com 583 módulos e bundle de aproximadamente 1,5 MB.
- A adaptação mobile preserva touch targets e grids próprios do React Native; telemetria persistida, fila local, capabilities, DTCs, findings, alerts e readiness têm hierarquia/origem distintas.
- Nenhuma mudança cosmética ampla foi feita. A única alteração visual atende uma inconsistência semântica e de proveniência real.
- Próxima etapa: iniciar a demo preservando o H2, executar o fallback autoassertivo e os 3 E2E, depois rodar suítes/build backend completos, Redocly e `git diff --check`; registrar CHECKPOINT 4.

### CHECKPOINT 4 — fluxo técnico integrado e validado

- A lacuna de disponibilidade foi fechada: `STALE` agora existe no domínio Java, simulador TypeScript, tipos compartilhados, OpenAPI e UI. Após cinco segundos sem amostra, o último valor é preservado como antigo sem alterar a capability descoberta.
- O estado térmico agora parte próximo da temperatura ambiente e aquece progressivamente. `OVERHEAT` altera a dinâmica térmica e atingiu 106,5 °C na demo sem salto direto de UI.
- O batch mobile passou a transportar opcionalmente o snapshot OBD da mesma ECU que gerou as amostras. O backend registra capabilities, DTCs, MIL, freeze frame e readiness antes da ingestão, bloqueia concorrência com seu simulador local e fornece o mesmo snapshot ao web.
- O dashboard deixou de usar `simulationRunning` como sinônimo de conexão veicular: conexão e leitura atual consideram a fonte recebida e a janela de freshness; dados expirados permanecem visíveis como último valor.
- Teste HTTP novo comprovou mobile ECU/batch → persistência → finding `ENGINE_TEMPERATURE_HIGH` → alerta `ENGINE_OVERHEAT` → dashboard, mantendo o DTC `P0217` separado.
- Validação final aprovada: backend 34/34 e package, web lint + 12/12 + build, mobile typecheck + 6/6, OpenAPI válida, Playwright 3/3, `git diff --check` e demo NORMAL → OVERHEAT.
- Serviços encerrados ao final; portas 8080 e 5173 livres.

## Objetivo

Consolidar os Blocos 1–4 como uma única implementação coerente, validar o fluxo ECU simulada → telemetria → backend → persistência → finding → alerta → SSE → web/mobile, corrigir somente falhas concretas e deixar continuidade reproduzível.

## Concluído

- Estado inicial, documentação, contrato e Git lidos antes de novas alterações.
- Blocos 1–4 encontrados implementados conforme descrito no CHECKPOINT 1.
- ECU/backend consolidados e cobertura causal ampliada; fluxo mobile deixou de misturar leitura não persistida com dashboard do backend.
- Web lint/test/build e mobile typecheck/test/export aprovados após a consolidação.
- Snapshot OBD mobile e telemetria agora percorrem o mesmo boundary de batch; web consome o estado efetivamente publicado pela fonte ativa.
- Cold start, estados completos de disponibilidade e fluxo ao vivo foram implementados e validados.

## Parcial

- PostgreSQL real continua sem execução neste host por indisponibilidade de Docker/`psql`.

## Não iniciado

- Integração física Bluetooth/ELM327, fora do escopo executável deste ambiente.

## Arquivos alterados

- `AGENT_HANDOFF.md`: criado/atualizado como checkpoint de continuidade.
- `apps/backend/src/test/.../StatefulVehicleSimulatorTest.java`: cobertura de frenagem, idle, carga e readiness.
- `apps/mobile/src/vehicle-data/StatefulObdSimulator.test.ts`: teste equivalente ao Java.
- `apps/mobile/App.tsx`: telemetria apenas após persistência e separação visual DTC/finding/alert, capabilities e readiness.
- `StatefulVehicleSimulator` Java/TypeScript: cold start, aquecimento progressivo e disponibilidade `STALE`.
- `TelemetryController`, `SimulationService`, API mobile e tipos compartilhados: snapshot OBD no batch e ownership único da fonte.
- `DashboardController`, `apps/web/src/App.tsx`, `obd.ts` e `CapabilityPanel.tsx`: conexão/freshness reais e estado stale visível.
- `contracts/openapi.yaml`, `scripts/demo-flow.ps1` e ADR 006: contrato e operação atualizados.
- Principais alterações preexistentes preservadas: simulador/backend em `apps/backend/.../simulator`, telemetria/vehicle/notes/statistics, migrations V3/V4, `apps/web/src/App.tsx`, `VehicleProfilePage.tsx`, `CapabilityPanel.tsx`, `NotesPanel.tsx`, `apps/mobile`, tipos/clientes compartilhados, `contracts/openapi.yaml`, Compose/scripts e documentação.

## Decisões

- Não refazer pesquisa ou código já validado.
- Não simular perfil, FIPE, providers, notas ou fotos.
- Manter DTC da ECU, finding do Enhara e alert como conceitos distintos.
- Priorizar integração e consistência automotiva; mudanças visuais apenas se uma inspeção revelar problema funcional ou responsivo.
- Snapshot OBD corrente é técnico e efêmero; telemetria/findings/alerts continuam persistidos. Uma nova sessão redescobre capabilities.
- Backend simulator e ECU mobile não podem ser fontes simultâneas para o mesmo veículo.

## Estado funcional

- Backend 34/34 e package aprovados; web 12/12, lint/build e Playwright 3/3 aprovados; mobile typecheck e simulador 6/6 aprovados.
- Demo H2 aprovada em 13,1 s/19 ticks de OVERHEAT: 106,5 °C, alerta crítico, finding, Health `CRITICAL` e viagem finalizada.
- Serviços locais encerrados; portas 8080 e 5173 livres.

## Problemas conhecidos

- Docker e `psql` indisponíveis; PostgreSQL real não pode ser exercitado neste host.
- Bluetooth/ELM327 físico não integrado.
- Advertência Redocly preexistente no endpoint de listagem de veículos sem resposta 4XX.
- Alterações acumuladas ainda não commitadas.

## Testes executados

- Backend focado: 17/17 passaram; integração HTTP adicional: 3/3.
- Backend completo/package: 34/34 e JAR executável gerado.
- Mobile simulador: 6/6 passaram.
- Mobile typecheck: passou.
- Web lint: passou; Vitest 12/12; build Vite: passou.
- Playwright: 3/3; demo-flow: aprovado; OpenAPI: válida com 1 warning preexistente; `git diff --check`: passou.
- Mobile export Android do CHECKPOINT 3: passou, 583 módulos, bundle Hermes ~1,5 MB.

## Testes pendentes

- Compose/PostgreSQL e Testcontainers somente quando Docker estiver disponível.

## Git

- Branch: `master`, alinhada a `origin/master`.
- Status: 42 arquivos rastreados modificados e diversos arquivos novos dos Blocos 2–4; nada staged.
- `git diff --stat`: 42 files changed, 2232 insertions(+), 574 deletions(-).
- Último commit: `c6483d7 chore: harden and document cp1 demo`.

## PRÓXIMA AÇÃO EXATA

Revisar e organizar o diff acumulado para commit. Quando Docker estiver disponível, executar `docker compose up -d --build` e repetir o smoke do fluxo contra PostgreSQL real.
