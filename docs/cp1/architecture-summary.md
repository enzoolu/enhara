# Resumo da arquitetura

- **Java e Spring Boot:** fornecem validação, transações, persistência, segurança, health checks e SSE em uma base madura e testável.
- **React:** atende o dashboard responsivo com componentes simples, atualização incremental por SSE e build estático.
- **React Native:** permite compartilhar TypeScript e regras de contrato sem impedir a futura implementação nativa de Bluetooth.
- **PostgreSQL:** oferece integridade relacional e índices adequados ao histórico temporal de telemetria.
- **Monólito modular:** mantém amostra, diagnóstico e alerta na mesma transação enquanto preserva fronteiras por feature.
- **SSE:** é suficiente para o canal unidirecional servidor → dashboard, usa `EventSource` nativo e requer menos infraestrutura que WebSocket.
- **Sem microserviços:** o CP1 não possui escala ou equipes que justifiquem coordenação distribuída e consistência eventual.
- **Mock OBD:** torna o fluxo reproduzível sem hardware, implementando a mesma porta `VehicleDataSource` prevista para Bluetooth/ELM327.
- **Escalabilidade:** os índices suportam latest/history; batching reduz chamadas; retenção ou particionamento pode ser adicionado ao PostgreSQL. Somente fronteiras comprovadamente pressionadas devem ser extraídas depois.

O diagrama e as decisões detalhadas estão em [architecture.md](../architecture/architecture.md).
