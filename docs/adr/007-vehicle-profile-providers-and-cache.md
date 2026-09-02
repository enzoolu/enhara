# ADR 007 — Perfil veicular, providers e cache persistente

Status: aceito

## Contexto

A tela Meu Carro precisa combinar cadastro, VIN observado por OBD real e dados externos sem confundir essas origens. BrasilAPI/FIPE e NHTSA vPIC têm coberturas e disponibilidades diferentes, e a telemetria central não pode depender delas.

## Decisão

- Manter o enriquecimento no módulo `vehicle`, atrás das portas `VehicleDataProvider` e `FipeCatalogProvider`.
- Usar BrasilAPI/FIPE para o catálogo guiado e valor FIPE, e NHTSA vPIC somente para atributos realmente devolvidos pelo decode de VIN.
- Persistir campos normalizados por chave com source, provider, URL de referência, `retrievedAt`, expiração e confirmação do usuário.
- Persistir o payload normalizado do enriquecimento em `vehicle_provider_cache`. Cache válido evita nova chamada; se uma atualização falhar, o último resultado é usado e marcado `CACHE_STALE` quando expirado.
- Preservar campos `USER_PROVIDED` e campos já confirmados quando um provider retorna valores diferentes.
- Consultar o catálogo FIPE somente por ação explícita na tela. A escolha Marca → Modelo/Versão → Ano/Combustível termina em um detalhe que passa pelo mesmo cache persistente do código FIPE.
- Aceitar VIN opcional no cadastro. O endpoint `profile/ecu-vin` é um boundary exclusivo para futura fonte OBD real; a ECU simulada não o alimenta.
- Armazenar fotos do usuário no filesystem configurado e metadados no banco; não usar imagens seed ou mock.

## Consequências

Meu Carro continua funcional offline com cadastro, fotos, notas e último enriquecimento persistido. A indisponibilidade de provider aparece como estado local e não bloqueia telemetria, diagnósticos ou dashboard. O catálogo guiado não fica disponível offline sem uma consulta anterior, mas o cadastro manual continua sendo o fallback obrigatório.

O vPIC não é tratado como cobertura universal para veículos brasileiros. A FIPE não é usada como catálogo de potência, torque, marchas, tanque, peso ou redline.

