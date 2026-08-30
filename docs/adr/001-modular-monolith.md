# ADR 001 — Monólito modular

Status: aceito

## Context

Telemetria, diagnóstico e alerta formam um fluxo transacional e o CP1 precisa ser simples de executar e demonstrar.

## Decision

Usar uma aplicação Spring Boot com pacotes por feature e fronteiras explícitas, sem serviços distribuídos.

## Consequences

O deploy e os testes permanecem simples. Uma extração futura exigirá contratos entre módulos, mas só ocorrerá quando escala ou organização justificarem.
