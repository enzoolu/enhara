# Roteiro de demonstração CP1

Tempo sugerido: 6–8 minutos.

## Preparação

1. Nesta máquina validada: `reset-demo.cmd` para H2, backend e web.
2. Em máquina com Docker previamente validado: `docker compose up --build`.
3. Confirme `/actuator/health`, deixe o dashboard aberto e o terminal em `scripts`.

## Narrativa

1. **Problema (30 s):** dados automotivos são técnicos; Enhara converte leituras em orientação preventiva, sem alegar causalidade não comprovada.
2. **Arquitetura (45 s):** mostre `docs/architecture.md` e destaque um corte vertical transacional, OpenAPI e SSE.
3. **Estado normal (60 s):** selecione Normal e inicie. Aponte velocidade, RPM, temperatura, bateria, histórico e conexão ao vivo.
4. **Incidente (90 s):** selecione Superaquecimento. A elevação é gradual; ao alcançar 105 °C, `ENGINE_TEMPERATURE_HIGH` fica ativo e um alerta crítico aparece via SSE.
5. **Ação (30 s):** reconheça o alerta. Explique que o histórico permanece e que outro alerta igual não é aberto enquanto já houver um aberto.
6. **Mobile (60 s):** inicie a ECU simulada, mostre conexão/fila/lote e troque para Bateria baixa. Ao parar, mostre o resumo da viagem.
7. **Engenharia (60 s):** apresente Flyway, testes verdes, contrato validado e Compose PostgreSQL.
8. **Limites (30 s):** Bluetooth real, identidade, GPS real e validação PostgreSQL nesta máquina estão conscientemente fora do CP1.

## Plano B

- Se o cenário agendado demorar: `./scripts/demo-flow.ps1` gera ticks determinísticos.
- Se o mobile físico não acessar a LAN: use o simulador do backend pelo dashboard.
- Se Docker não estiver disponível: use o perfil H2 `demo`; deixe claro que Compose e driver PostgreSQL são o caminho de produção local.
- Se SSE for bloqueado por proxy: recarregue o dashboard para obter o snapshot REST e demonstre os dados persistidos.
