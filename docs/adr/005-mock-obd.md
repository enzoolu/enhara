# ADR 005 — Mock OBD determinístico

Status: aceito

## Context

Hardware OBD-II não pode ser requisito para desenvolver, testar ou apresentar o corte vertical.

## Decision

Fornecer cenários graduais `NORMAL`, `OVERHEAT` e `LOW_BATTERY` pela mesma porta usada por uma futura fonte Bluetooth.

## Consequences

A demonstração é reproduzível e testa regras reais. O mock não comprova compatibilidade física, que permanece explicitamente no roadmap.
