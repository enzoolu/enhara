import { StatusBar } from 'expo-status-bar';
import { useFonts } from 'expo-font';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { API_URL, api } from './src/api';
import type { DashboardData, SimulatedObdSnapshot, Telemetry, TelemetryInput, Vehicle, VehicleScenario } from './src/types';
import { MockVehicleDataSource } from './src/vehicle-data/MockVehicleDataSource';
import type { VehicleDataSource } from './src/vehicle-data/VehicleDataSource';

const number = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

const palette = {
  background: '#F2F7F5',
  surface: '#FFFFFF',
  surfaceSubtle: '#F2F7F5',
  text: '#07100E',
  textSecondary: '#62766F',
  muted: '#8EA39D',
  accent: '#C78CFF',
  accentSoft: '#C78CFF24',
  success: '#2CC981',
  successSoft: '#61E7A929',
  warning: '#FFBD66',
  warningSoft: '#FFBD662E',
  danger: '#FF7474',
  dangerSoft: '#FF747424',
  line: '#D7E6E1',
  lineSoft: '#ADC0BA6B',
  white: '#FFFFFF',
} as const;

const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;
const radii = { sm: 8, md: 10, lg: 12, xl: 18, pill: 999 } as const;
const type = { regular: 'Inter', medium: 'Inter-Medium', semibold: 'Inter-SemiBold', bold: 'Inter-Bold' } as const;

export default function App() {
  const [fontsLoaded] = useFonts({
    Inter: require('../../docs/skillui/enhara-design/fonts/Inter-Regular.ttf'),
    'Inter-Medium': require('../../docs/skillui/enhara-design/fonts/Inter-Medium.ttf'),
    'Inter-SemiBold': require('../../docs/skillui/enhara-design/fonts/Inter-SemiBold.ttf'),
    'Inter-Bold': require('../../docs/skillui/enhara-design/fonts/Inter-Bold.ttf'),
  });
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [vehicleId, setVehicleId] = useState('');
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [localRunning, setLocalRunning] = useState(false);
  const [scenario, setScenario] = useState<VehicleScenario>('NORMAL');
  const [queuedSamples, setQueuedSamples] = useState(0);
  const [lastBatchSize, setLastBatchSize] = useState(0);
  const [lastBatchAt, setLastBatchAt] = useState<Date | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [connection, setConnection] = useState<'connecting' | 'online' | 'offline'>('connecting');
  const [obd, setObd] = useState<SimulatedObdSnapshot | null>(null);
  const dataSource = useRef<VehicleDataSource>(new MockVehicleDataSource());
  const queue = useRef<TelemetryInput[]>([]);
  const flushInFlight = useRef<Promise<boolean> | null>(null);
  const vehicleIdRef = useRef('');

  useEffect(() => {
    vehicleIdRef.current = vehicleId;
  }, [vehicleId]);

  const flushQueue = useCallback((): Promise<boolean> => {
    const selectedVehicle = vehicleIdRef.current;
    if (!selectedVehicle || !queue.current.length) return Promise.resolve(true);
    if (flushInFlight.current) return flushInFlight.current;

    const operation = (async () => {
      setSyncing(true);
      const batch = queue.current.splice(0, 50);
      setQueuedSamples(queue.current.length);
      try {
        const result = await api.ingestBatch(selectedVehicle, batch, dataSource.current.obdSnapshot());
        setLastBatchSize(result.acceptedSamples);
        setLastBatchAt(new Date());
        setConnection('online');
        try {
          const [dashboardData, obdData] = await Promise.all([api.dashboard(selectedVehicle), api.obdState(selectedVehicle)]);
          setData(dashboardData);
          setObd(obdData);
        } catch (refreshReason) {
          setError(refreshReason instanceof Error ? refreshReason.message : 'Telemetria persistida; falha ao atualizar o painel');
          setConnection('offline');
        }
        return true;
      } catch (reason) {
        queue.current.unshift(...batch);
        setQueuedSamples(queue.current.length);
        setError(reason instanceof Error ? reason.message : 'Falha ao sincronizar o lote de telemetria');
        setConnection('offline');
        return false;
      } finally {
        flushInFlight.current = null;
        setSyncing(false);
      }
    })();
    flushInFlight.current = operation;
    return operation;
  }, []);

  const drainQueue = useCallback(async (): Promise<boolean> => {
    while (flushInFlight.current || queue.current.length) {
      if (!await (flushInFlight.current ?? flushQueue())) return false;
    }
    return true;
  }, [flushQueue]);

  const load = useCallback(async (silent = false) => {
    try {
      if (!silent) setError('');
      let selected = vehicleId;
      if (!selected) {
        const available = await api.vehicles();
        setVehicles(available);
        selected = available[0]?.id || '';
        if (selected) setVehicleId(selected);
      }
      if (selected) {
        const [dashboardData, obdData] = await Promise.all([api.dashboard(selected), api.obdState(selected)]);
        setData(dashboardData);
        setObd(obdData);
      }
      setConnection('online');
    } catch (reason) {
      if (!silent) setError(reason instanceof Error ? reason.message : 'Falha ao conectar');
      setConnection('offline');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [vehicleId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!vehicleId) return;
    const interval = setInterval(() => void load(true), 2_000);
    return () => clearInterval(interval);
  }, [vehicleId, load]);

  useEffect(() => {
    const interval = setInterval(() => void flushQueue(), 4_000);
    return () => clearInterval(interval);
  }, [flushQueue]);

  useEffect(() => () => dataSource.current.stop(), []);

  async function toggleLocalSource() {
    if (localRunning) {
      dataSource.current.stop();
      setLocalRunning(false);
      if (!await drainQueue()) {
        setError('A ECU foi parada, mas a viagem continua aberta porque ainda há telemetria sem sincronizar. Tente novamente quando a API estiver disponível.');
        return;
      }
      try {
        await api.finishTrip(vehicleId);
        await load(true);
      } catch (reason) {
        setError(reason instanceof Error ? reason.message : 'Falha ao finalizar a viagem');
      }
      return;
    }
    if (!vehicleId) return;
    try {
      await api.stopBackendSimulation(vehicleId);
      await api.startTrip(vehicleId);
      dataSource.current.simulation?.setScenario(scenario);
      dataSource.current.start((reading) => {
        queue.current.push(reading);
        setQueuedSamples(queue.current.length);
        if (queue.current.length >= 5) void flushQueue();
      });
      setLocalRunning(true);
    } catch (reason) {
      setConnection('offline');
      setError(reason instanceof Error ? reason.message : 'Falha ao iniciar a viagem');
    }
  }

  function chooseScenario(nextScenario: VehicleScenario) {
    setScenario(nextScenario);
    dataSource.current.simulation?.setScenario(nextScenario);
  }

  async function acknowledge(alertId: string) {
    try {
      await api.acknowledge(vehicleId, alertId);
      setData((current) => current && ({ ...current, openAlerts: current.openAlerts.filter((item) => item.id !== alertId) }));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Não foi possível reconhecer o alerta');
    }
  }

  if (!fontsLoaded || (loading && !data)) {
    return <SafeAreaView style={styles.loading}><StatusBar style="dark" /><Brand /><ActivityIndicator color={palette.accent} /><Text style={styles.muted}>Conectando a {API_URL}</Text></SafeAreaView>;
  }

  const currentVehicleReading = Boolean(data?.vehicleDataConnected && data.latestTelemetry
    && Date.now() - Date.parse(data.latestTelemetry.recordedAt) <= 5_000);
  const supports = (key: string) => obd?.capabilities.some((item) => item.key === key && item.status === 'SUPPORTED') ?? false;

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar style="dark" />
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={refreshing} tintColor={palette.accent} onRefresh={() => { setRefreshing(true); void load(); }} />}
      >
        <View style={styles.header}>
          <Brand />
          <View style={[styles.online, connection === 'offline' && styles.offline]}>
            <View style={[styles.onlineDot, connection === 'offline' && styles.offlineDot]} />
            <Text style={[styles.onlineText, connection === 'offline' && styles.offlineText]}>{connection === 'online' ? 'BACKEND ONLINE' : connection === 'offline' ? 'OFFLINE' : 'CONECTANDO'}</Text>
          </View>
        </View>

        {error ? <View style={styles.error}><Text style={styles.errorText}>{error}</Text><Text style={styles.errorHint}>Confira EXPO_PUBLIC_API_URL e a rede local.</Text></View> : null}

        {data ? (
          <>
            <Text style={styles.eyebrow}>MEU VEÍCULO</Text>
            <Text style={styles.title}>{data.vehicle.name}</Text>
            <Text style={styles.subtitle}>{data.vehicle.manufacturer} {data.vehicle.model} · {data.vehicle.modelYear} · {data.vehicle.licensePlate}</Text>

            {vehicles.length > 1 && <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.vehicleTabs}>
              {vehicles.map((vehicle) => <Pressable key={vehicle.id} onPress={() => { setObd(null); setVehicleId(vehicle.id); }} style={[styles.vehicleTab, vehicle.id === vehicleId && styles.vehicleTabActive]}><Text style={styles.vehicleTabText}>{vehicle.name}</Text></Pressable>)}
            </ScrollView>}

            <View style={[styles.vehicleConnection, currentVehicleReading ? styles.vehicleConnectionLive : styles.vehicleConnectionLast]}>
              <View style={[styles.onlineDot, !currentVehicleReading && styles.vehicleConnectionLastDot]} />
              <View style={styles.connectionCopy}><Text style={styles.connectionTitle}>{currentVehicleReading ? 'ECU conectada · leitura atual' : data.latestTelemetry ? 'ECU desconectada · último valor válido' : 'Aguardando a primeira leitura'}</Text><Text style={styles.connectionDetail}>{data.latestTelemetry ? `${telemetryOriginLabel(data.latestTelemetry.source)} · ${new Date(data.latestTelemetry.recordedAt).toLocaleString('pt-BR')}` : 'Nenhum valor foi sintetizado para preencher os cards.'}</Text></View>
            </View>

            <View style={styles.heroCard}>
              <View>
                <Text style={styles.cardLabel}>SAÚDE GERAL</Text>
                <View style={styles.healthLine}>
                  <Text style={[styles.healthValue, data.health.status === 'CRITICAL' && styles.healthCritical, data.health.status === 'ATTENTION' && styles.healthAttention]}>{data.health.score}</Text>
                  <Text style={styles.healthUnit}>/100</Text>
                </View>
                <Text style={styles.healthCaption}>{data.health.label}</Text>
                <Text style={styles.healthExplanation}>{data.health.explanation}</Text>
              </View>
              <View style={styles.carOrb}><Text style={styles.carGlyph}>⌁</Text></View>
            </View>

            <Text style={[styles.eyebrow, styles.telemetryLabel]}>{currentVehicleReading ? 'LEITURA ATUAL DA ECU' : data.latestTelemetry ? 'ÚLTIMOS VALORES VÁLIDOS' : 'SEM TELEMETRIA PERSISTIDA'}</Text>
            <View style={styles.metrics}>
              {supports('VEHICLE_SPEED') && <Metric label="VELOCIDADE" value={data.latestTelemetry ? number.format(data.latestTelemetry.speedKph) : '—'} unit="km/h" color={palette.accent} />}
              {supports('ENGINE_SPEED') && <Metric label="ROTAÇÃO" value={data.latestTelemetry ? number.format(data.latestTelemetry.rpm) : '—'} unit="rpm" color={palette.textSecondary} />}
              {supports('ENGINE_COOLANT_TEMPERATURE') && <Metric label="ARREFECIMENTO" value={data.latestTelemetry ? number.format(data.latestTelemetry.engineTempC) : '—'} unit="°C" color={data.latestTelemetry && data.latestTelemetry.engineTempC >= 105 ? palette.danger : palette.warning} />}
              {supports('CONTROL_MODULE_VOLTAGE') && <Metric label="TENSÃO DO MÓDULO" value={data.latestTelemetry ? number.format(data.latestTelemetry.batteryVoltage) : '—'} unit="V" color={palette.accent} />}
              {supports('CALCULATED_ENGINE_LOAD') && <Metric label="CARGA DO MOTOR" value={data.latestTelemetry ? number.format(data.latestTelemetry.engineLoadPercent) : '—'} unit="%" color={palette.textSecondary} />}
              {supports('THROTTLE_POSITION') && <Metric label="ACELERADOR" value={data.latestTelemetry ? number.format(data.latestTelemetry.throttlePositionPercent) : '—'} unit="%" color={palette.success} />}
              {!obd && <View style={styles.metricUnavailable}><Text style={styles.emptyText}>Descobrindo parâmetros suportados pela ECU…</Text></View>}
            </View>

            <Text style={[styles.eyebrow, styles.scenarioLabel]}>CENÁRIO DA ECU SIMULADA</Text>
            <View style={styles.scenarios}>
              {(['NORMAL', 'OVERHEAT', 'LOW_VOLTAGE', 'MISFIRE'] as VehicleScenario[]).map((item) => (
                <Pressable key={item} onPress={() => chooseScenario(item)} style={[styles.scenario, scenario === item && styles.scenarioActive]}>
                  <Text style={[styles.scenarioText, scenario === item && styles.scenarioTextActive]}>{item === 'NORMAL' ? 'Normal' : item === 'OVERHEAT' ? 'Superaquecimento' : item === 'MISFIRE' ? 'Falha de combustão' : 'Tensão baixa'}</Text>
                </Pressable>
              ))}
            </View>

            <Pressable style={[styles.simulationButton, localRunning && styles.simulationButtonActive]} onPress={toggleLocalSource}>
              <View style={[styles.simDot, localRunning && styles.simDotActive]} />
              <Text style={[styles.simulationText, localRunning && styles.simulationTextActive]}>{localRunning ? 'Parar ECU simulada' : 'Iniciar ECU simulada'}</Text>
            </Pressable>
            <Text style={styles.queueStatus}>{syncing ? 'enviando lote' : `${queuedSamples} na fila`} · último lote: {lastBatchSize} {lastBatchAt ? `às ${lastBatchAt.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}` : ''} · {dataSource.current.name}</Text>

            <SectionTitle kicker="CAPABILITY-AWARE" title="Dados disponíveis pela ECU" count={obd?.capabilities.filter((item) => item.status === 'SUPPORTED').length ?? 0} />
            <View style={styles.card}>
              {!obd ? <Empty title="Capabilities ainda não descobertas" text="Aguarde a leitura persistida do estado OBD." /> : (
                <View style={styles.capabilitySummary}>
                  <CapabilityCount label="Disponíveis" value={obd.capabilities.filter((item) => item.availability === 'SUPPORTED').length} tone="supported" />
                  <CapabilityCount label="Sem dados" value={obd.capabilities.filter((item) => item.availability === 'SUPPORTED_NO_DATA').length} tone="unknown" />
                  <CapabilityCount label="Dados antigos" value={obd.capabilities.filter((item) => item.availability === 'STALE').length} tone="stale" />
                  <CapabilityCount label="Não suportados" value={obd.capabilities.filter((item) => item.status === 'UNSUPPORTED').length} tone="unsupported" />
                  <CapabilityCount label="Não descobertos" value={obd.capabilities.filter((item) => item.status === 'UNKNOWN').length} tone="unknown" />
                </View>
              )}
            </View>

            <SectionTitle kicker="RESUMOS" title="Viagens recentes" count={data.recentTrips.filter((trip) => trip.endedAt).length} />
            <View style={styles.card}>
              {data.activeTrip ? <View style={styles.activeTrip}><View style={styles.onlineDot} /><View><Text style={styles.alertTitle}>Viagem em andamento</Text><Text style={styles.alertMessage}>As métricas serão fechadas ao parar a ECU.</Text></View></View> : null}
              {!data.recentTrips.some((trip) => trip.endedAt) ? <Empty title="Nenhuma viagem concluída" text="Inicie e pare a ECU simulada para gerar um resumo." /> : data.recentTrips.filter((trip) => trip.endedAt).slice(0, 3).map((trip) => (
                <View style={styles.tripRow} key={trip.id}>
                  <View><Text style={styles.tripValue}>{number.format(trip.distanceKm)} km</Text><Text style={styles.alertMessage}>{number.format(trip.averageSpeedKph)} km/h média</Text></View>
                  <View style={styles.tripScore}><Text style={styles.tripScoreLabel}>SCORE EXP.</Text><Text style={styles.tripScoreValue}>{trip.drivingScore}</Text></View>
                </View>
              ))}
            </View>

            <SectionTitle kicker="ATENÇÃO" title="Alertas abertos" count={data.openAlerts.length} />
            <View style={styles.card}>
              {!data.openAlerts.length ? <Empty title="Nenhum alerta aberto" text="As leituras estão dentro dos limites." /> : data.openAlerts.map((alert) => (
                <View style={styles.alertRow} key={alert.id}>
                  <View style={[styles.alertMark, alert.severity === 'CRITICAL' && styles.alertMarkCritical]}><Text style={styles.alertBang}>!</Text></View>
                  <View style={styles.alertCopy}><Text style={styles.alertTitle}>{alert.title}</Text><Text style={styles.alertMessage}>{alert.message}</Text></View>
                  <Pressable accessibilityLabel="Reconhecer alerta" onPress={() => acknowledge(alert.id)} style={styles.ack}><Text style={styles.ackText}>✓</Text></Pressable>
                </View>
              ))}
            </View>

            <SectionTitle kicker="CÓDIGOS DA ECU" title="DTCs da ECU simulada" count={obd?.dtcs.length ?? 0} />
            <View style={styles.card}>
              {!obd ? <Empty title="Sem leitura da ECU" text="A memória OBD ainda não chegou ao backend." /> : !obd.dtcs.length ? <Empty title="Nenhum DTC registrado" text={`MIL ${obd.milOn ? 'acesa' : 'apagada'} · a ECU não registrou código neste ciclo.`} /> : obd.dtcs.map((dtc) => (
                <View style={styles.diagnosticRow} key={`${dtc.code}-${dtc.firstDetectedAt}`}>
                  <Text style={styles.code}>{dtc.code}</Text>
                  <View style={styles.alertCopy}><Text style={styles.alertTitle}>{dtc.description}</Text><Text style={styles.alertMessage}>{dtc.statuses.join(' · ')} · {dtc.active ? 'condição presente' : 'memória'} · MIL {obd.milOn ? 'acesa' : 'apagada'}</Text><Text style={styles.alertMessage}>Primeira: {new Date(dtc.firstDetectedAt).toLocaleString('pt-BR')} · última: {new Date(dtc.lastDetectedAt).toLocaleString('pt-BR')}</Text>{dtc.freezeFrame ? <Text style={styles.alertMessage}>Freeze frame: {new Date(dtc.freezeFrame.capturedAt).toLocaleString('pt-BR')}</Text> : null}</View>
                </View>
              ))}
            </View>

            <SectionTitle kicker="FINDINGS DO ENHARA" title="Condições detectadas" count={data.activeDiagnostics.length} />
            <View style={styles.card}>
              {!data.activeDiagnostics.length ? <Empty title="Nenhum finding ativo" text="As regras do Enhara não detectaram condição anormal na telemetria persistida." /> : data.activeDiagnostics.map((finding) => (
                <View style={styles.diagnosticRow} key={finding.id}>
                  <Text style={styles.code}>{finding.code}</Text>
                  <View style={styles.alertCopy}><Text style={styles.alertTitle}>{finding.description}</Text><Text style={styles.alertMessage}>Finding derivado · {finding.severity}</Text></View>
                </View>
              ))}
            </View>

            <SectionTitle kicker="MONITORES DA ECU" title="Readiness" count={obd?.readiness.filter((item) => item.status === 'READY').length ?? 0} />
            <View style={styles.card}>
              {!obd ? <Empty title="Readiness indisponível" text="A fonte veicular ainda não forneceu o estado dos monitores." /> : obd.readiness.map((item) => (
                <View style={styles.readinessRow} key={item.monitor}><Text style={styles.alertTitle}>{readinessLabel(item.monitor)}</Text><Text style={[styles.readinessStatus, item.status === 'READY' ? styles.readinessReady : item.status === 'NOT_SUPPORTED' ? styles.readinessUnsupported : null]}>{item.status === 'READY' ? 'Pronto' : item.status === 'NOT_SUPPORTED' ? 'Não suportado' : 'Não concluído'}</Text></View>
              ))}
            </View>

            <Text style={styles.footer}>Dashboard a cada 2 s · lotes a cada 4 s ou 5 amostras · {API_URL}</Text>
          </>
        ) : <Empty title="Nenhum veículo disponível" text="Execute o backend no perfil demo." />}
      </ScrollView>
    </SafeAreaView>
  );
}

function Brand() {
  return <View style={styles.brand}><View style={styles.brandMark}><Text style={styles.brandLetter}>E</Text></View><View><Text style={styles.brandName}>enhara</Text><Text style={styles.brandTag}>DRIVE WITH CLARITY</Text></View></View>;
}

function Metric({ label, value, unit, color }: { label: string; value: string; unit: string; color: string }) {
  return <View style={styles.metric}><Text style={styles.metricLabel}>{label}</Text><View style={styles.metricLine}><Text style={[styles.metricValue, { color }]}>{value}</Text><Text style={styles.metricUnit}>{unit}</Text></View><View style={[styles.metricBar, { backgroundColor: color }]} /></View>;
}

function SectionTitle({ kicker, title, count }: { kicker: string; title: string; count: number }) {
  return <View style={styles.sectionTitle}><View><Text style={styles.eyebrow}>{kicker}</Text><Text style={styles.sectionName}>{title}</Text></View><View style={styles.count}><Text style={styles.countText}>{count}</Text></View></View>;
}

function Empty({ title, text }: { title: string; text: string }) {
  return <View style={styles.empty}><View style={styles.emptyMark}><Text style={styles.emptyCheck}>✓</Text></View><Text style={styles.emptyTitle}>{title}</Text><Text style={styles.emptyText}>{text}</Text></View>;
}

function CapabilityCount({ label, value, tone }: { label: string; value: number; tone: 'supported' | 'unsupported' | 'unknown' | 'stale' }) {
  return <View style={styles.capabilityItem}><Text style={[styles.capabilityValue, tone === 'unsupported' && styles.capabilityUnsupported, tone === 'unknown' && styles.capabilityUnknown, tone === 'stale' && styles.capabilityStale]}>{value}</Text><Text style={styles.alertMessage}>{label}</Text></View>;
}

function telemetryOriginLabel(source: Telemetry['source']) {
  if (source === 'SIMULATED_OBD' || source === 'SIMULATOR') return 'ECU/OBD simulada';
  if (source === 'MOBILE') return 'ECU/OBD via mobile';
  return 'Integração via API';
}

function readinessLabel(monitor: string) {
  const labels: Record<string, string> = { MISFIRE: 'Falha de combustão', FUEL_SYSTEM: 'Sistema de combustível', COMPREHENSIVE_COMPONENT: 'Componentes abrangentes', CATALYST: 'Catalisador', OXYGEN_SENSOR: 'Sensor de oxigênio' };
  return labels[monitor] ?? monitor;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: palette.background },
  content: { paddingHorizontal: 20, paddingTop: spacing.lg, paddingBottom: 48 },
  loading: { flex: 1, backgroundColor: palette.background, alignItems: 'center', justifyContent: 'center', gap: spacing.xl, padding: spacing.xxl },
  muted: { color: palette.muted, fontFamily: type.regular, fontSize: 11, textAlign: 'center' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.xxl },
  brand: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  brandMark: { width: 36, height: 36, borderRadius: radii.md, alignItems: 'center', justifyContent: 'center', backgroundColor: palette.accent },
  brandLetter: { color: palette.white, fontFamily: type.bold, fontSize: 18 },
  brandName: { color: palette.text, fontFamily: type.bold, fontSize: 20, letterSpacing: -1 },
  brandTag: { color: palette.muted, fontFamily: type.semibold, fontSize: 6, letterSpacing: 1.3 },
  online: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 10, paddingVertical: 6, borderRadius: radii.pill, backgroundColor: palette.successSoft },
  onlineDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: palette.success },
  onlineText: { color: palette.success, fontFamily: type.bold, fontSize: 8, letterSpacing: 1 },
  offline: { backgroundColor: palette.dangerSoft },
  offlineDot: { backgroundColor: palette.danger },
  offlineText: { color: palette.danger },
  eyebrow: { color: palette.muted, fontFamily: type.bold, fontSize: 8, letterSpacing: 1.5 },
  title: { color: palette.text, fontFamily: type.bold, fontSize: 30, letterSpacing: -1.2, marginTop: spacing.xs },
  subtitle: { color: palette.textSecondary, fontFamily: type.regular, fontSize: 12, marginTop: spacing.xs, marginBottom: 20 },
  vehicleTabs: { marginBottom: spacing.md, flexGrow: 0 },
  vehicleTab: { marginRight: spacing.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm, borderRadius: radii.md, borderWidth: 1, borderColor: palette.line, backgroundColor: palette.surface },
  vehicleTabActive: { borderColor: palette.accent, backgroundColor: palette.accentSoft },
  vehicleTabText: { color: palette.textSecondary, fontFamily: type.semibold, fontSize: 10 },
  vehicleConnection: { minHeight: 60, flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginBottom: spacing.md, paddingHorizontal: spacing.md, paddingVertical: 10, borderRadius: radii.lg, borderWidth: 1 },
  vehicleConnectionLive: { borderColor: palette.lineSoft, backgroundColor: palette.successSoft },
  vehicleConnectionLast: { borderColor: palette.warning, backgroundColor: palette.warningSoft },
  vehicleConnectionLastDot: { backgroundColor: palette.warning },
  connectionCopy: { flex: 1 },
  connectionTitle: { color: palette.text, fontFamily: type.semibold, fontSize: 10 },
  connectionDetail: { color: palette.textSecondary, fontFamily: type.regular, fontSize: 9, lineHeight: 13, marginTop: spacing.xs },
  heroCard: { minHeight: 160, padding: spacing.xl, borderRadius: radii.xl, borderWidth: 1, borderColor: palette.lineSoft, backgroundColor: palette.surface, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', overflow: 'hidden' },
  cardLabel: { color: palette.muted, fontFamily: type.bold, fontSize: 8, letterSpacing: 1.5 },
  healthLine: { flexDirection: 'row', alignItems: 'flex-end', marginTop: 10 },
  healthValue: { color: palette.success, fontFamily: type.bold, fontSize: 48, lineHeight: 52, letterSpacing: -2 },
  healthAttention: { color: palette.warning },
  healthCritical: { color: palette.danger },
  healthUnit: { color: palette.muted, fontFamily: type.regular, fontSize: 10, marginBottom: spacing.sm, marginLeft: spacing.xs },
  healthCaption: { color: palette.text, fontFamily: type.semibold, fontSize: 11 },
  healthExplanation: { color: palette.textSecondary, fontFamily: type.regular, fontSize: 8, lineHeight: 12, marginTop: spacing.xs, maxWidth: 195 },
  carOrb: { width: 92, height: 92, borderRadius: 46, backgroundColor: palette.accentSoft, borderWidth: 1, borderColor: palette.accent, alignItems: 'center', justifyContent: 'center' },
  carGlyph: { color: palette.accent, fontFamily: type.regular, fontSize: 54, transform: [{ rotate: '-8deg' }] },
  metrics: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 10 },
  telemetryLabel: { marginTop: spacing.xl },
  metric: { width: '48.5%', minHeight: 108, padding: spacing.lg, borderRadius: 15, backgroundColor: palette.surface, borderWidth: 1, borderColor: palette.lineSoft, overflow: 'hidden' },
  metricUnavailable: { width: '100%', minHeight: 88, alignItems: 'center', justifyContent: 'center', padding: spacing.lg, borderRadius: 15, borderWidth: 1, borderStyle: 'dashed', borderColor: palette.line },
  metricLabel: { color: palette.muted, fontFamily: type.bold, fontSize: 8, letterSpacing: 1 },
  metricLine: { flexDirection: 'row', alignItems: 'flex-end', marginTop: spacing.md },
  metricValue: { fontFamily: type.bold, fontSize: 27, lineHeight: 31, letterSpacing: -1 },
  metricUnit: { color: palette.muted, fontFamily: type.regular, fontSize: 8, marginLeft: spacing.xs, marginBottom: spacing.xs },
  metricBar: { position: 'absolute', width: 24, height: 3, borderRadius: 2, right: spacing.lg, bottom: spacing.lg },
  scenarioLabel: { marginTop: spacing.xl, marginBottom: spacing.sm },
  scenarios: { flexDirection: 'row', gap: 6 },
  scenario: { flex: 1, minHeight: 44, paddingHorizontal: 6, borderRadius: 11, borderWidth: 1, borderColor: palette.line, alignItems: 'center', justifyContent: 'center', backgroundColor: palette.surface },
  scenarioActive: { borderColor: palette.accent, backgroundColor: palette.accentSoft },
  scenarioText: { color: palette.textSecondary, fontFamily: type.medium, fontSize: 8, textAlign: 'center' },
  scenarioTextActive: { color: palette.text, fontFamily: type.semibold },
  simulationButton: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing.sm, marginTop: spacing.md, padding: spacing.lg, borderRadius: radii.lg, borderWidth: 1, borderColor: palette.accent, backgroundColor: palette.surface },
  simulationButtonActive: { backgroundColor: palette.accent, borderColor: palette.accent },
  simulationText: { color: palette.accent, fontFamily: type.semibold, fontSize: 11 },
  simulationTextActive: { color: palette.white },
  simDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: palette.accent },
  simDotActive: { backgroundColor: palette.white },
  queueStatus: { color: palette.muted, fontFamily: type.regular, fontSize: 8, textAlign: 'center', marginTop: spacing.sm },
  sectionTitle: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: spacing.xxl, marginBottom: spacing.md },
  sectionName: { color: palette.text, fontFamily: type.bold, fontSize: 18, letterSpacing: -.5, marginTop: spacing.xs },
  count: { minWidth: 28, height: 28, paddingHorizontal: spacing.sm, borderRadius: radii.pill, backgroundColor: palette.accentSoft, alignItems: 'center', justifyContent: 'center' },
  countText: { color: palette.accent, fontFamily: type.bold, fontSize: 10 },
  card: { padding: 10, borderRadius: radii.xl, borderWidth: 1, borderColor: palette.lineSoft, backgroundColor: palette.surface },
  capabilitySummary: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, padding: spacing.xs },
  capabilityItem: { flexGrow: 1, flexBasis: '30%', minHeight: 72, alignItems: 'center', justifyContent: 'center', borderRadius: 11, backgroundColor: palette.surfaceSubtle },
  capabilityValue: { color: palette.success, fontFamily: type.bold, fontSize: 22 },
  capabilityUnsupported: { color: palette.muted },
  capabilityUnknown: { color: palette.warning },
  capabilityStale: { color: palette.accent },
  activeTrip: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: spacing.md, margin: spacing.xs, borderRadius: 11, borderWidth: 1, borderColor: palette.success, backgroundColor: palette.successSoft },
  tripRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: spacing.md, marginVertical: spacing.xs, borderRadius: 11, backgroundColor: palette.surfaceSubtle },
  tripValue: { color: palette.text, fontFamily: type.bold, fontSize: 13 },
  tripScore: { alignItems: 'flex-end', paddingLeft: spacing.lg, borderLeftWidth: 1, borderLeftColor: palette.line },
  tripScoreLabel: { color: palette.muted, fontFamily: type.semibold, fontSize: 7 },
  tripScoreValue: { color: palette.accent, fontFamily: type.bold, fontSize: 19 },
  alertRow: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 10, borderRadius: radii.lg, backgroundColor: palette.surfaceSubtle, marginVertical: spacing.xs },
  alertMark: { width: 33, height: 33, borderRadius: 9, backgroundColor: palette.warningSoft, alignItems: 'center', justifyContent: 'center' },
  alertMarkCritical: { backgroundColor: palette.dangerSoft },
  alertBang: { color: palette.warning, fontFamily: type.bold, fontSize: 15 },
  alertCopy: { flex: 1 },
  alertTitle: { color: palette.text, fontFamily: type.semibold, fontSize: 10 },
  alertMessage: { color: palette.textSecondary, fontFamily: type.regular, fontSize: 9, marginTop: 3 },
  ack: { width: 29, height: 29, borderRadius: 9, borderWidth: 1, borderColor: palette.line, backgroundColor: palette.surface, alignItems: 'center', justifyContent: 'center' },
  ackText: { color: palette.success, fontFamily: type.bold },
  diagnosticRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, padding: spacing.md, marginVertical: spacing.xs },
  readinessRow: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing.md, borderBottomWidth: 1, borderBottomColor: palette.lineSoft },
  readinessStatus: { color: palette.warning, fontFamily: type.bold, fontSize: 9 },
  readinessReady: { color: palette.success },
  readinessUnsupported: { color: palette.muted },
  code: { color: palette.accent, backgroundColor: palette.accentSoft, paddingHorizontal: spacing.sm, paddingVertical: 6, borderRadius: 7, fontFamily: type.bold, fontSize: 10 },
  empty: { alignItems: 'center', justifyContent: 'center', paddingVertical: spacing.xl, paddingHorizontal: spacing.lg },
  emptyMark: { width: 40, height: 40, borderRadius: 20, borderWidth: 1, borderColor: palette.accent, backgroundColor: palette.accentSoft, alignItems: 'center', justifyContent: 'center' },
  emptyCheck: { color: palette.accent, fontFamily: type.bold, fontSize: 16 },
  emptyTitle: { color: palette.text, fontFamily: type.semibold, fontSize: 11, marginTop: 10 },
  emptyText: { color: palette.textSecondary, fontFamily: type.regular, fontSize: 9, marginTop: 3, textAlign: 'center' },
  error: { padding: spacing.md, borderRadius: radii.lg, backgroundColor: palette.dangerSoft, borderWidth: 1, borderColor: palette.danger, marginBottom: 20 },
  errorText: { color: palette.danger, fontFamily: type.semibold, fontSize: 11 },
  errorHint: { color: palette.textSecondary, fontFamily: type.regular, fontSize: 8, marginTop: spacing.xs },
  footer: { color: palette.muted, fontFamily: type.regular, fontSize: 8, textAlign: 'center', marginTop: spacing.xxl },
});
