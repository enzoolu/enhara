import { StatefulObdSimulator } from './StatefulObdSimulator';

const START = Date.parse('2026-08-31T12:00:00Z');

function ticks(simulator: StatefulObdSimulator, count: number, offset = 0) {
  return Array.from({ length: count }, (_, index) =>
    simulator.tick(new Date(START + (offset + index) * 1_000)));
}

function check(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function test(name: string, body: () => void): void {
  body();
  console.log(`PASS ${name}`);
}

test('troca ascendente reduz RPM sem interromper a velocidade', () => {
  const simulator = new StatefulObdSimulator();
  const frames = ticks(simulator, 40);
  const shiftIndex = frames.findIndex((frame) => {
    const state = frame.snapshot.vehicleState;
    return state.shifting
      && state.shiftedFromGear !== null
      && state.shiftedToGear !== null
      && state.shiftedToGear > state.shiftedFromGear;
  });

  check(shiftIndex > 0, 'uma troca ascendente deveria ocorrer');
  const before = frames[shiftIndex - 1].snapshot.vehicleState;
  const after = frames[shiftIndex].snapshot.vehicleState;
  check(after.gear > before.gear, 'a marcha deveria aumentar');
  check(after.rpm < before.rpm, 'o RPM deveria cair na troca');
  check(after.speedKph >= before.speedKph, 'a velocidade não deveria cair na troca');
  check(frames.slice(shiftIndex + 1, shiftIndex + 5)
    .some((frame) => frame.snapshot.vehicleState.rpm > after.rpm), 'o RPM deveria voltar a subir');
});

test('frenagem desacelera até idle e completa readiness sem quebrar causalidade', () => {
  const simulator = new StatefulObdSimulator();
  const frames = ticks(simulator, 55);
  const brakingIndex = frames.findIndex((frame) => frame.snapshot.driverInput.brakePercent > 0);

  check(brakingIndex > 0, 'uma fase de frenagem deveria ocorrer');
  check(frames[brakingIndex].telemetry.speedKph < frames[brakingIndex - 1].telemetry.speedKph,
    'o brake deveria reduzir a velocidade');
  const stopped = frames.find((frame) => frame.snapshot.vehicleState.speedKph === 0
    && frame.snapshot.driverInput.throttlePercent === 0);
  check(stopped, 'o veículo deveria chegar ao repouso');
  check(stopped.snapshot.vehicleState.gear === 1, 'o repouso deveria ocorrer em primeira marcha');
  check(stopped.snapshot.vehicleState.rpm === 800, 'o motor deveria estabilizar no idle do perfil');
  check(stopped.snapshot.vehicleState.engineLoadPercent >= 8
    && stopped.snapshot.vehicleState.engineLoadPercent <= 15, 'a carga em idle deveria permanecer plausível');
  check(stopped.snapshot.readiness.every((item) => item.status === 'READY'),
    'os monitores suportados deveriam completar readiness no ciclo normal');
});

test('perfis controlam capabilities e não emitem PIDs unsupported', () => {
  const full = new StatefulObdSimulator('COMPACT_GASOLINE').snapshot();
  const limitedSimulator = new StatefulObdSimulator('COMPACT_GASOLINE_LIMITED');
  const limited = limitedSimulator.tick(new Date(START)).snapshot;

  check(full.capabilities.find((item) => item.key === 'MAF_AIR_FLOW_RATE')?.status === 'SUPPORTED',
    'o perfil completo deveria suportar MAF');
  check(limited.capabilities.find((item) => item.key === 'MAF_AIR_FLOW_RATE')?.status === 'UNSUPPORTED',
    'o perfil limitado deveria marcar MAF como unsupported');
  check(['SUPPORTED', 'UNSUPPORTED', 'UNKNOWN'].every((status) =>
    full.capabilities.some((item) => item.status === status)), 'o perfil completo deveria expor os três estados');
  check(!limited.liveData.some((item) => item.key === 'MAF_AIR_FLOW_RATE'),
    'PID unsupported não deveria aparecer em live data');
  check(limited.vehicleInformation === null, 'perfil sem VIN suportado não deveria inventar Vehicle Information');
});

test('cold start aquece progressivamente e leituras antigas ficam stale', () => {
  const simulator = new StatefulObdSimulator();
  const initial = simulator.snapshot(new Date(START));
  check(initial.vehicleState.coolantTemperatureC >= 24 && initial.vehicleState.coolantTemperatureC <= 30,
    'o coolant deveria iniciar próximo da temperatura ambiente');
  check(initial.capabilities.find((item) => item.key === 'ENGINE_SPEED')?.availability === 'SUPPORTED_NO_DATA',
    'PID suportado deveria iniciar sem dados');

  simulator.tick(new Date(START));
  const stale = simulator.snapshot(new Date(START + 6_000));
  check(stale.capabilities.find((item) => item.key === 'ENGINE_SPEED')?.availability === 'STALE',
    'a capability deveria indicar último dado antigo');
  check(stale.liveData.find((item) => item.key === 'ENGINE_SPEED')?.availability === 'STALE',
    'o live data deveria preservar o valor e marcá-lo como antigo');
  check(stale.capabilities.find((item) => item.key === 'ENGINE_OIL_TEMPERATURE')?.availability === 'UNSUPPORTED',
    'stale não deveria alterar capability unsupported');
});

test('cenários obrigatórios alteram veículo e ECU sem GPS inventado', () => {
  const normal = new StatefulObdSimulator();
  const normalFrame = ticks(normal, 8).at(-1)!;
  check(normalFrame.telemetry.source === 'SIMULATED_OBD', 'a provenance deveria ser SIMULATED_OBD');
  check(normalFrame.telemetry.latitude === null && normalFrame.telemetry.longitude === null,
    'a ECU simulada não deveria fabricar GPS');
  check(normalFrame.snapshot.dtcs.length === 0, 'NORMAL não deveria criar DTC');

  const overheat = new StatefulObdSimulator();
  overheat.setScenario('OVERHEAT');
  const hotFrame = ticks(overheat, 36).at(-1)!;
  check(hotFrame.telemetry.engineTempC >= 105, 'OVERHEAT deveria elevar progressivamente o coolant');
  check(hotFrame.snapshot.dtcs.some((dtc) => dtc.code === 'P0217'), 'OVERHEAT deveria qualificar P0217');

  const lowVoltage = new StatefulObdSimulator();
  lowVoltage.setScenario('LOW_VOLTAGE');
  const voltageFrame = ticks(lowVoltage, 12).at(-1)!;
  check(voltageFrame.telemetry.batteryVoltage < 11.8, 'LOW_VOLTAGE deveria reduzir control module voltage');
  check(voltageFrame.snapshot.dtcs.some((dtc) => dtc.code === 'P0562'), 'LOW_VOLTAGE deveria qualificar P0562');

  const misfire = new StatefulObdSimulator();
  misfire.setScenario('MISFIRE');
  const misfireFrame = ticks(misfire, 6).at(-1)!;
  const dtc = misfireFrame.snapshot.dtcs[0];
  check(dtc.code === 'P0300', 'MISFIRE deveria qualificar P0300');
  check(['PENDING', 'CONFIRMED', 'PERMANENT'].every((status) =>
    dtc.statuses.includes(status as typeof dtc.statuses[number])), 'P0300 deveria cumprir o lifecycle completo');
  check((dtc.freezeFrame?.values.length ?? 0) > 0, 'P0300 deveria capturar freeze frame');
  check(misfireFrame.snapshot.milOn, 'DTC confirmado deveria acender MIL');
});

test('recorrência após limpeza cria novo evento e freeze frame', () => {
  const simulator = new StatefulObdSimulator();
  simulator.setScenario('MISFIRE');
  const firstEvent = ticks(simulator, 6).at(-1)!.snapshot.dtcs[0];

  simulator.setScenario('NORMAL');
  check(ticks(simulator, 6, 6).at(-1)!.snapshot.dtcs.length === 0, 'DTC deveria ser limpo após os passes');

  simulator.setScenario('MISFIRE');
  const recurrence = ticks(simulator, 2, 12).at(-1)!.snapshot.dtcs[0];
  check(Date.parse(recurrence.firstDetectedAt) > Date.parse(firstEvent.firstDetectedAt),
    'a recorrência deveria ter novo firstDetectedAt');
  check(Date.parse(recurrence.freezeFrame!.capturedAt) > Date.parse(firstEvent.freezeFrame!.capturedAt),
    'a recorrência deveria capturar novo freeze frame');
  check(recurrence.statuses.length === 1 && recurrence.statuses[0] === 'PENDING',
    'a recorrência deveria reiniciar em pending');
});
