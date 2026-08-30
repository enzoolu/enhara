# ADR 002 — PostgreSQL e Flyway

Status: aceito

## Context

O produto precisa relacionar veículos, amostras, diagnósticos e alertas e consultar séries por veículo e instante.

## Decision

Usar PostgreSQL como banco principal, Flyway como única fonte de evolução do schema e índice em `(vehicle_id, recorded_at)`.

## Consequences

Há integridade relacional e consultas temporais eficientes. Retenção e particionamento serão definidos por medições; H2 fica restrito a testes e fallback local.
