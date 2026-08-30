# ADR 003 — React Native e porta veicular

Status: aceito

## Context

O CP1 precisa de uma ECU reproduzível, mas a evolução prevê Bluetooth/OBD-II nativo.

## Decision

Usar React Native com TypeScript e fazer a coleta depender de `VehicleDataSource`, com implementações mock e futura Bluetooth.

## Consequences

A UI e o batching podem ser validados sem hardware. Bluetooth ainda exigirá permissões, adaptador real e testes nativos.
