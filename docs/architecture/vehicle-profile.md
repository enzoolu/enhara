# Meu Carro — identidade, provenance e operação offline

## Fluxo

1. Um adaptador OBD real pode registrar um VIN estruturalmente válido em `POST /profile/ecu-vin`. O simulador não chama esse boundary.
2. Um VIN disponível permite enriquecimento pelo NHTSA vPIC. Somente campos não vazios retornados pelo provider são normalizados.
3. Para FIPE, o usuário pode informar um código exato ou navegar no catálogo BrasilAPI por tipo, marca, modelo/versão e ano/combustível.
4. O usuário pode corrigir qualquer campo cadastral. Uma correção vira `USER_PROVIDED`, é confirmada no mesmo instante e não é sobrescrita por providers.
5. VIN é opcional. Fabricante, modelo, ano, versão/motorização e combustível podem ser informados manualmente sem depender da internet.

## Persistência

- `vehicle_profile_fields`: valor normalizado e provenance por campo/veículo.
- `vehicle_provider_cache`: resultado normalizado por provider e chave de consulta, com `fetched_at` e `expires_at`.
- `vehicle_provider_statuses`: estado da última tentativa (`LIVE`, `CACHE_FRESH`, `CACHE_STALE`, `UNAVAILABLE`, `CONFLICT` ou `NOT_REQUESTED`).
- `vehicle_photos`: metadados persistidos; o conteúdo fica sob `VEHICLE_PHOTOS_DIR` e sobrevive a reinicializações normais.

Campos básicos ainda existentes em `vehicles` aparecem como `VEHICLE_REGISTRATION`. Dados FIPE e vPIC mantêm fontes distintas e provider explícito. VIN observado por adaptador real aparece como `ECU_OBD`; nenhuma resposta externa recebe essa origem.

## Cache e falhas

BrasilAPI/FIPE usa TTL de 24 horas; NHTSA vPIC usa 30 dias. Uma leitura de perfil nunca chama a internet. O enriquecimento só ocorre em ação explícita. Quando uma chamada falha:

- cache existente é devolvido, com data original e indicação de stale quando expirado;
- sem cache, apenas o provider fica `UNAVAILABLE`;
- cadastro, fotos, notas, telemetria e demais módulos continuam disponíveis.

Se fabricante/modelo decodificados do VIN conflitam com uma correção manual ou identificação FIPE já persistida, nenhum atributo adicional do vPIC é aplicado e o provider fica `CONFLICT` para revisão.

## Limites de fonte

BrasilAPI/FIPE fornece identificação comercial, combustível, código, referência e valor quando a resposta é válida. NHTSA vPIC é alimentado por dados submetidos por fabricantes e pode ter cobertura limitada fora do mercado dos EUA. Nenhum dos dois autoriza inferir potência, torque, marchas, tanque, peso ou redline.

Referências: [BrasilAPI/FIPE](https://brasilapi.com.br/docs) e [NHTSA vPIC Vehicle API](https://vpic.nhtsa.dot.gov/api/).
