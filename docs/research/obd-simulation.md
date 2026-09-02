# Pesquisa técnica — simulação stateful ECU/OBD

Atualizado em 31/08/2026. Este documento registra as fontes e os limites usados na primeira implementação da camada simulada.

## Conclusões adotadas

- OBD genérico é orientado a diagnóstico de emissões/propulsão e não implica suporte universal a PIDs. Cada perfil da simulação classifica todo PID conhecido como `SUPPORTED`, `UNSUPPORTED` ou `UNKNOWN`; somente os suportados produzem live data.
- O discovery usa a semântica dos bitmaps de PIDs suportados. A documentação do ELM327 confirma que Mode 01 PID 00 informa os PIDs suportados e que veículos não precisam oferecer todos os modos/PIDs.
- O catálogo inicial usa somente endereços documentados: Mode 01 PIDs `04`, `05`, `06`, `07`, `0B`, `0C`, `0D`, `0F`, `10`, `11`, `14`, `2F`, `33`, `42`, `44`, `5C`, e Mode 09 PID `02`. Presença no catálogo não significa capability suportada.
- Serviços permanecem separados por função: live data (Mode 01), freeze frame (Mode 02), DTC confirmado (Mode 03), DTC pending (Mode 07), informação veicular (Mode 09) e DTC permanent (Mode 0A).
- DTC é memória da ECU. Finding é interpretação determinística do Enhara e Alert é comunicação acionável; o endpoint do snapshot OBD não grava P0300/P0217/P0562 como finding ou alert automaticamente.
- Freeze frame é capturado quando a falha qualifica como pending e mantido quando ela amadurece para confirmed. A regulamentação CARB permite o vínculo do frame a pending ou confirmed em SAE J1979 e detalha sua retenção na maturação.
- Readiness representa se o monitor suportado completou desde limpeza/reset. `NOT_SUPPORTED` não pode ser promovido a ready e desligar o veículo não deve, por si só, limpar readiness.
- Permanent DTC não é apagado como um código comum. Na simulação, ele só desaparece após a condição deixar de existir e o monitor responsável completar uma sequência de aprovação.
- Depois que todos os status de um DTC são removidos, a entrada encerrada deixa a memória ativa da simulação. Uma recorrência inicia novo evento, com novos timestamps e novo freeze frame; não reutiliza evidência da ocorrência anterior.

## DTCs usados nos cenários

| Cenário | Código ECU | Significado documentado | Permanent nesta simulação |
|---|---|---|---|
| `MISFIRE` | `P0300` | Random/Multiple Cylinder Misfire Detected | sim |
| `OVERHEAT` | `P0217` | Engine Coolant Over Temperature Condition | não |
| `LOW_VOLTAGE` | `P0562` | System Voltage Low | não |

Os códigos são genéricos documentados. A descrição não afirma a causa mecânica da condição.

## Modelo físico mínimo

O passo determinístico segue:

`Driver/Input -> Vehicle Dynamics -> Engine/Transmission -> ECU state -> OBD services -> Enhara`

- O ciclo do condutor tem fases explícitas de aceleração, cruzeiro, frenagem e parada; não usa random nem senoide.
- Força nas rodas deriva de torque, throttle, curva de torque aproximada, relação da marcha, relação final, eficiência e raio do pneu.
- Aceleração deriva da força motriz menos resistência ao rolamento, arrasto aerodinâmico e frenagem.
- RPM deriva da rotação da roda e da relação total, respeitando idle e redline. Durante troca, a marcha mais longa reduz RPM e uma pequena transferência de torque/inércia mantém a velocidade contínua.
- Carga deriva de throttle e demanda de aceleração. MAP deriva da abertura do throttle; MAF deriva de cilindrada, RPM, eficiência volumétrica aproximada, densidade do ar e MAP.
- Temperatura do coolant integra geração de calor e resfriamento por diferença térmica/fluxo de ar. `OVERHEAT` reduz a eficiência de arrefecimento e adiciona carga térmica progressiva.
- Tensão converge para o alvo do alternador em `NORMAL`; `LOW_VOLTAGE` reduz a saída elétrica gradualmente. O valor continua denominado control module voltage no snapshot OBD.
- Nível de combustível cai a partir do fluxo de ar, lambda/AFR e densidade aproximada da gasolina; não é uma rampa independente.
- `MISFIRE` reduz eficiência de combustão, desloca lambda/fuel trim e aplica ripple determinístico de RPM ligado ao estado do motor.

## Limites conscientes

- O modelo é plausível e testável, não um simulador veicular de homologação nem um modelo de precisão científica.
- Os contadores de 2/4/6 amostras para pending/confirmed/permanent aceleram a demonstração. Eles representam qualificação e ciclos de aprovação, mas não alegam reproduzir os drive cycles e critérios de certificação de um fabricante.
- O VIN em Vehicle Information é permitido apenas dentro do perfil `SIMULATED_OBD`; não deve preencher o cadastro real do usuário sem indicação explícita de origem simulada.
- Marcha e pedal de freio são estado interno da simulação, não PIDs OBD-II genéricos inventados.
- `LOW_BATTERY` foi mantido somente como alias de compatibilidade; a nomenclatura correta do cenário e da evidência é `LOW_VOLTAGE`/tensão baixa do sistema.

## Fontes

- [SAE J1979 — E/E Diagnostic Test Modes](https://saemobilus.sae.org/standards/j1979_199709-e-e-diagnostic-test-modes): escopo, modos de teste, current data, freeze frame, DTC e vehicle information.
- [SAE J1979-DA](https://saemobilus.sae.org/standards/j1979da_202504-j1979-da-digital-annex-e-e-diagnostic-test-modes): registry de identificadores regulados de emissões e propulsão.
- [SAE J2012](https://saemobilus.sae.org/standards/j2012_202509-diagnostic-trouble-code-definitions): formato e conjunto padronizado de DTCs.
- [ELM327 datasheet](https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf): modos 01–0A, discovery por PID 00, exemplos de coolant/RPM e ausência de suporte universal.
- [CARB OBD II Final Regulation Order](https://ww2.arb.ca.gov/sites/default/files/barcu/regact/2021/obd2021/fro-obdii.pdf): pending/confirmed/permanent, MIL, freeze frame e readiness.
- [EPA — OBD readiness best practices](https://www.epa.gov/system/files/documents/2022-08/diesel-obd-im-readiness-14k-pounds-gwr-best-practices.pdf): permanent DTC só é removido depois que o monitor responsável executa e confirma a ausência da falha.
- [NHTSA/VW OBD data — P0300](https://static.nhtsa.gov/odi/tsbs/2013/MC-10243913-9999.pdf), [NHTSA/Ford — P0217](https://static.nhtsa.gov/odi/rcl/2017/RCMN-17V209-7886.pdf) e [NHTSA/Ford — P0562](https://static.nhtsa.gov/odi/inv/2004/INRD-EA04006-18710P.pdf): confirmação independente das descrições usadas.
