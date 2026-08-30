# Evidências do CP1

Execução local em 30/08/2026, Windows, perfil Spring `demo` e H2 em memória.

## Matriz comprovada

| Verificação | Resultado observado |
|---|---|
| Backend `mvnw.cmd test` | 9/9 testes passaram; 0 falhas, 0 erros, 0 ignorados |
| Backend `mvnw.cmd package` | `BUILD SUCCESS`; JAR executável gerado |
| Flyway | migrations v1 e v2 aplicadas no H2 antes do Hibernate validar o schema |
| Web `npm run lint` | passou com zero warnings |
| Web `npm test -- --run` | 5/5 testes passaram em 2 arquivos |
| Web `npm run build` | build Vite concluído |
| E2E Playwright | 1/1 passou; execução do teste em 13,3 s |
| Mobile `npm run typecheck` | passou sem erros |
| Mobile Android export | bundle Hermes gerado; 582 módulos; arquivo de aproximadamente 1,5 MB |
| OpenAPI | contrato 3.1 válido no Redocly 2.49.0; uma advertência estilística preexistente no `GET /api/vehicles` |

## Cenário exercitado

Fluxo autoassertivo executado por `scripts/demo-flow.ps1`:

1. leitura NORMAL observada em 93,9 °C e 13,6 V;
2. transição gradual para OVERHEAT;
3. temperatura final de 106,0 °C;
4. `ENGINE_TEMPERATURE_HIGH` ativo;
5. um `ENGINE_OVERHEAT` crítico aberto e deduplicado;
6. Health `Situação crítica`, score 5/100;
7. Trip finalizada e resumo persistido;
8. saída `DEMO FALLBACK APROVADA`.

Tempo observado na validação final até o alerta no fallback acelerado: **2,7 s em 4 ticks**, com intervalo configurado de 650 ms e o agendador do simulador ativo. Na execução E2E agendada pelo backend, o alerta chegou ao dashboard via SSE e o teste completo passou em 13,3 s.

Screenshot comprovado após o E2E: [dashboard.png](dashboard.png).

## Banco de dados

- Validado nesta máquina: **H2 em memória**, compatibilidade PostgreSQL, Flyway v1/v2 e `ddl-auto=validate`.
- Não validado nesta máquina: **PostgreSQL real**. `docker` e `psql` não estavam disponíveis no PATH em 30/08/2026.
- Implementado, mas não contado como evidência de execução: Compose PostgreSQL 17, driver, perfil `dev`, migration v2 e Testcontainers no classpath.

Nenhuma evidência acima pressupõe Bluetooth/OBD físico, GPS real, serviço externo ou PostgreSQL não executado.
