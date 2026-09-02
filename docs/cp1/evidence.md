# Evidências do CP1

Última execução local em 01/09/2026, Windows, perfil Spring `demo` e H2 persistente em arquivo.

## Matriz comprovada

| Verificação | Resultado observado |
|---|---|
| Backend `mvnw.cmd test` | 37/37 testes passaram; 0 falhas e 0 erros |
| Backend `mvnw.cmd package` | 37/37 testes passaram novamente; `BUILD SUCCESS`; JAR executável gerado |
| Flyway | migrations v1 a v4 aplicadas no H2 antes do Hibernate validar o schema |
| Web `npm run lint` | passou com zero warnings |
| Web `npm test -- --run` | 13/13 testes passaram em 4 arquivos |
| Web `npm run build` | build Vite concluído |
| E2E Playwright | 3/3 passaram: fluxo desktop ao vivo, responsividade em 390 × 844 px e Meu Carro persistente |
| Mobile `npm run typecheck` | passou sem erros |
| Mobile `npm test -- --run` | 6/6 testes do simulador causal passaram |
| Mobile Android export | bundle Hermes gerado; 583 módulos; arquivo de aproximadamente 1,5 MB |
| OpenAPI | contrato 3.1 válido no Redocly 2.49.0; uma advertência estilística preexistente no `GET /api/vehicles` |

## Cenário exercitado

Fluxo autoassertivo executado por `scripts/demo-flow.ps1`:

1. leitura NORMAL observada em 43,6 °C e 13,4 V;
2. transição gradual para OVERHEAT;
3. temperatura final de 105,9 °C;
4. `ENGINE_TEMPERATURE_HIGH` ativo;
5. um `ENGINE_OVERHEAT` crítico aberto e deduplicado;
6. Health `Situação crítica`, score 5/100;
7. Trip finalizada e resumo persistido;
8. saída `DEMO FALLBACK APROVADA`.

Tempo observado na validação final até o alerta no fluxo acelerado: **14,2 s em 21 ticks**, com intervalo configurado de 650 ms e o agendador do simulador ativo. Na execução E2E, o alerta e a resolução de finding chegaram ao dashboard via SSE; os três testes passaram em 16,3 s.

Screenshots comprovados após o E2E: [Painel Principal](dashboard.png), [Minhas Estatísticas](statistics.png) e [Meu Carro](my-car.png).

## Meu Carro exercitado

O fluxo de 01/09/2026 comprovou:

1. catálogo guiado e detalhe real da BrasilAPI/FIPE, com código, valor e mês de referência;
2. consulta real do NHTSA vPIC e bloqueio de uma identidade incompatível, registrada como `CONFLICT`;
3. provenance por campo sem apresentar dado externo como ECU e sem potência, torque ou outras especificações inventadas;
4. precedência e confirmação da correção manual `USER_PROVIDED`;
5. cache persistido reutilizado sem nova chamada e fallback com os providers deliberadamente offline;
6. perfil, correção manual e foto preservados após reinicialização normal da demo;
7. foto PNG persistida servida pelo endpoint de conteúdo com HTTP 200;
8. tela Meu Carro funcional no navegador online e offline.

## Painel e estatísticas exercitados

O E2E de 01/09/2026 comprovou:

1. conexão SSE e leitura atual no Painel Principal;
2. alerta `ENGINE_OVERHEAT` e finding `ENGINE_TEMPERATURE_HIGH` separados;
3. estado “Última leitura válida” após parar a ECU simulada;
4. DTC `P0300` vindo da memória da ECU, com explicação simples separada;
5. distância e velocidade máxima derivadas dos registros; consumo indisponível por insuficiência de dados;
6. capability `UNSUPPORTED` e `UNKNOWN` sem interação, sem cards de pneu ou fluido de freio;
7. criação de nota persistida e marcada como informação do usuário;
8. Painel e Estatísticas sem overflow horizontal em viewport de 390 × 844 px.

## Banco de dados

- Validado nesta máquina: **H2 persistente em arquivo**, compatibilidade PostgreSQL, Flyway v1/v2/v3/v4 e `ddl-auto=validate`.
- Não validado nesta máquina: **PostgreSQL real**. `docker` e `psql` não estavam disponíveis no PATH em 30/08/2026.
- Implementado, mas não contado como evidência de execução: Compose PostgreSQL 17, driver, perfil `dev`, migration v2 e Testcontainers no classpath.

BrasilAPI/FIPE e NHTSA vPIC foram exercitados pela internet. Nenhuma evidência acima pressupõe Bluetooth/OBD físico, GPS real ou PostgreSQL não executado.
