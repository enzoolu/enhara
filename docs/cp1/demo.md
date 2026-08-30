# Demonstração do CP1

## Caminho principal

1. Execute `docker compose up --build` e confirme `http://localhost:8080/actuator/health`.
2. Abra `http://localhost:5173` e selecione o veículo fictício.
3. Selecione **Normal** para iniciar a ECU simulada e observe as leituras e o gráfico.
4. Selecione **Superaquecimento**. A temperatura sobe gradualmente até ultrapassar 105 °C.
5. Confirme que o diagnóstico `ENGINE_TEMPERATURE_HIGH` e o alerta crítico aparecem sem recarregar a página.
6. Reconheça o alerta e confirme a atualização em tempo real.

O roteiro narrativo completo está em [demo-script.md](demo-script.md).

## Plano B sem Docker

```powershell
cd apps/backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Em outro terminal:

```powershell
cd apps/web
npm ci
npm run dev
```

O fluxo determinístico também pode ser acionado com `./scripts/demo-flow.ps1`. O perfil `demo` usa H2 em memória e mantém o mesmo contrato HTTP, as mesmas regras e o mesmo fluxo SSE usados com PostgreSQL.
