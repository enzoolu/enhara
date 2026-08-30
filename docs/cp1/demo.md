# Demonstração do CP1

## Preparação única

Com Java 21+ e Node.js/npm instalados, execute na raiz:

```powershell
npm ci
cd apps/backend
.\mvnw.cmd package -DskipTests
cd ..\..
```

Não é necessário internet durante a apresentação depois dessa preparação.

## Reset da demo

Na raiz do repositório:

```powershell
.\reset-demo.cmd
```

Resultado esperado:

- backend `UP` em http://127.0.0.1:8080/actuator/health;
- dashboard em http://127.0.0.1:5173;
- H2 recriado sem alertas antigos;
- veículo `Enhara Demo Car` disponível;
- cenário `NORMAL` e simulador auxiliar ativos.

Se o código do backend mudou desde o último JAR, use `powershell -File scripts/reset-demo.ps1 -Build`.

## Demo principal — Mobile/Simulator → Backend → Dashboard

1. Execute `reset-demo.cmd` e abra o dashboard.
2. Descubra o IP LAN do computador com `ipconfig`.
3. No terminal do mobile:

   ```powershell
   cd apps/mobile
   $env:EXPO_PUBLIC_API_URL='http://IP-LAN-DO-PC:8080'
   npm start
   ```

4. No app, confirme `BACKEND ONLINE`, cenário `Normal` e fila em zero.
5. Toque em **Iniciar ECU simulada**. O app encerra o simulador auxiliar do backend para evitar duas fontes concorrentes, inicia uma Trip e envia lotes.
6. Mostre velocidade/RPM/temperatura/bateria variando e o horário do último lote aceito.
7. Selecione **Superaquecimento**. A temperatura sobe gradualmente.
8. No dashboard, confirme sem recarregar:
   - alerta `Temperatura do motor elevada`;
   - diagnóstico `ENGINE_TEMPERATURE_HIGH`;
   - Saúde do Veículo em `Situação crítica`;
   - conexão `API conectada`.
9. Toque em **Parar ECU simulada** e mostre o resumo em **Viagens recentes**.

## Demo fallback — Script Simulator → Backend → Dashboard

Mantenha o dashboard aberto e execute na raiz:

```powershell
.\scripts\demo-flow.ps1
```

O script limpa interferências lógicas, valida NORMAL, produz a subida gradual de OVERHEAT, espera o alerta, confere diagnóstico/Health, encerra a Trip e falha com exit code diferente de zero se qualquer asserção não for atendida.

Saída final esperada: `DEMO FALLBACK APROVADA`.

## Encerrar

```powershell
.\stop-demo.cmd
```

Os logs permanecem em `.data/demo/backend.out.log`, `backend.err.log`, `web.out.log` e `web.err.log`.

## Troubleshooting rápido

### Backend não iniciou

- Abra `.data/demo/backend.err.log` e `.data/demo/backend.out.log`.
- Confirme `java -version` e a existência de `apps/backend/target/enhara-api-0.0.1-SNAPSHOT.jar`.
- Recompile com `powershell -File scripts/reset-demo.ps1 -Build`.

### Porta ocupada

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -State Listen
```

Execute `stop-demo.cmd`. Se o processo não pertence ao Enhara, encerre-o conscientemente ou altere a porta fora do horário da apresentação.

### Dashboard não conecta

- Confirme o health do backend.
- Recarregue `http://127.0.0.1:5173`.
- Verifique `.data/demo/web.err.log`.
- Não abra o HTML compilado diretamente; use o Vite iniciado pelo script.

### Banco vazio

Execute `reset-demo.cmd`. O perfil `demo` recria o H2 e aplica Flyway v1/v2 antes de criar o seed.

### SSE não conecta

- Confirme `API conectada` no canto inferior esquerdo.
- Abra diretamente `/api/vehicles/{vehicleId}/events` apenas para diagnóstico.
- Recarregue o dashboard; o snapshot REST preserva os dados enquanto o EventSource reconecta.

### Alerta antigo interfere

Execute `reset-demo.cmd`. Como alternativa sem reiniciar, o `demo-flow.ps1` reconhece alertas abertos antes de começar.

### Mobile não encontra o backend

- Dispositivo físico: use `EXPO_PUBLIC_API_URL=http://IP-LAN-DO-PC:8080`, não `localhost`.
- Emulador Android: o padrão é `http://10.0.2.2:8080`.
- Confirme que computador e celular estão na mesma rede e que o firewall permite a porta 8080.
- Faça reload completo do Expo após alterar `EXPO_PUBLIC_API_URL`, pois a variável é embutida no bundle.

### Mobile indisponível

Use imediatamente a Demo fallback; ela percorre o mesmo contrato HTTP, regras, persistência e SSE sem hardware ou serviço externo.
