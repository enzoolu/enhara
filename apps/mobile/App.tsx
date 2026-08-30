import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
import type { DashboardData, Telemetry, TelemetryInput, Vehicle, VehicleScenario } from './src/types';
import { MockVehicleDataSource } from './src/vehicle-data/MockVehicleDataSource';

const number = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

export default function App() {
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
  const dataSource = useRef(new MockVehicleDataSource());
  const queue = useRef<TelemetryInput[]>([]);
  const flushInFlight = useRef(false);
  const vehicleIdRef = useRef('');

  useEffect(() => {
    vehicleIdRef.current = vehicleId;
  }, [vehicleId]);

  const flushQueue = useCallback(async () => {
    const selectedVehicle = vehicleIdRef.current;
    if (!selectedVehicle || flushInFlight.current || !queue.current.length) return;
    flushInFlight.current = true;
    const batch = queue.current.splice(0, 50);
    setQueuedSamples(queue.current.length);
    try {
      const result = await api.ingestBatch(selectedVehicle, batch);
      setLastBatchSize(result.acceptedSamples);
    } catch (reason) {
      queue.current.unshift(...batch);
      setQueuedSamples(queue.current.length);
      setError(reason instanceof Error ? reason.message : 'Falha ao sincronizar o lote de telemetria');
    } finally {
      flushInFlight.current = false;
    }
  }, []);

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
      if (selected) setData(await api.dashboard(selected));
    } catch (reason) {
      if (!silent) setError(reason instanceof Error ? reason.message : 'Falha ao conectar');
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

  const health = useMemo(() => {
    if (!data) return 0;
    const penalty = data.openAlerts.reduce((total, alert) => total + (alert.severity === 'CRITICAL' ? 22 : 10), 0);
    return Math.max(20, 100 - penalty - data.activeDiagnostics.length * 6);
  }, [data]);

  function toggleLocalSource() {
    if (localRunning) {
      dataSource.current.stop();
      setLocalRunning(false);
      void flushQueue();
      return;
    }
    if (!vehicleId) return;
    dataSource.current.setScenario(scenario);
    dataSource.current.start((reading) => {
      queue.current.push(reading);
      setQueuedSamples(queue.current.length);
      const localReading: Telemetry = { ...reading, id: -Date.now(), vehicleId: vehicleIdRef.current };
      setData((current) => current && ({
        ...current,
        latestTelemetry: localReading,
        telemetryHistory: [...current.telemetryHistory.slice(-19), localReading],
      }));
      if (queue.current.length >= 5) void flushQueue();
    });
    setLocalRunning(true);
  }

  function chooseScenario(nextScenario: VehicleScenario) {
    setScenario(nextScenario);
    dataSource.current.setScenario(nextScenario);
  }

  async function acknowledge(alertId: string) {
    try {
      await api.acknowledge(vehicleId, alertId);
      setData((current) => current && ({ ...current, openAlerts: current.openAlerts.filter((item) => item.id !== alertId) }));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Não foi possível reconhecer o alerta');
    }
  }

  if (loading && !data) {
    return <SafeAreaView style={styles.loading}><StatusBar style="light" /><Brand /><ActivityIndicator color="#48EFA0" /><Text style={styles.muted}>Conectando a {API_URL}</Text></SafeAreaView>;
  }

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar style="light" />
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={refreshing} tintColor="#48EFA0" onRefresh={() => { setRefreshing(true); void load(); }} />}
      >
        <View style={styles.header}>
          <Brand />
          <View style={styles.online}><View style={styles.onlineDot} /><Text style={styles.onlineText}>AO VIVO</Text></View>
        </View>

        {error ? <View style={styles.error}><Text style={styles.errorText}>{error}</Text><Text style={styles.errorHint}>Confira EXPO_PUBLIC_API_URL e a rede local.</Text></View> : null}

        {data ? (
          <>
            <Text style={styles.eyebrow}>MEU VEÍCULO</Text>
            <Text style={styles.title}>{data.vehicle.name}</Text>
            <Text style={styles.subtitle}>{data.vehicle.manufacturer} {data.vehicle.model} · {data.vehicle.modelYear} · {data.vehicle.licensePlate}</Text>

            {vehicles.length > 1 && <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.vehicleTabs}>
              {vehicles.map((vehicle) => <Pressable key={vehicle.id} onPress={() => setVehicleId(vehicle.id)} style={[styles.vehicleTab, vehicle.id === vehicleId && styles.vehicleTabActive]}><Text style={styles.vehicleTabText}>{vehicle.name}</Text></Pressable>)}
            </ScrollView>}

            <View style={styles.heroCard}>
              <View>
                <Text style={styles.cardLabel}>SAÚDE GERAL</Text>
                <View style={styles.healthLine}><Text style={styles.healthValue}>{health}</Text><Text style={styles.healthUnit}>/100</Text></View>
                <Text style={styles.healthCaption}>{health >= 85 ? 'Tudo sob controle' : health >= 60 ? 'Requer atenção' : 'Ação recomendada'}</Text>
              </View>
              <View style={styles.carOrb}><Text style={styles.carGlyph}>⌁</Text></View>
            </View>

            <View style={styles.metrics}>
              <Metric label="VELOCIDADE" value={data.latestTelemetry ? number.format(data.latestTelemetry.speedKph) : '—'} unit="km/h" color="#48EFA0" />
              <Metric label="ROTAÇÃO" value={data.latestTelemetry ? number.format(data.latestTelemetry.rpm) : '—'} unit="rpm" color="#68A9FF" />
              <Metric label="MOTOR" value={data.latestTelemetry ? number.format(data.latestTelemetry.engineTempC) : '—'} unit="°C" color={data.latestTelemetry && data.latestTelemetry.engineTempC >= 105 ? '#FF6B6B' : '#FFB85C'} />
              <Metric label="BATERIA" value={data.latestTelemetry ? number.format(data.latestTelemetry.batteryVoltage) : '—'} unit="V" color="#B28BFF" />
              <Metric label="CARGA" value={data.latestTelemetry ? number.format(data.latestTelemetry.engineLoadPercent) : '—'} unit="%" color="#68A9FF" />
              <Metric label="ACELERADOR" value={data.latestTelemetry ? number.format(data.latestTelemetry.throttlePositionPercent) : '—'} unit="%" color="#48EFA0" />
            </View>

            <Text style={[styles.eyebrow, styles.scenarioLabel]}>CENÁRIO DA ECU SIMULADA</Text>
            <View style={styles.scenarios}>
              {(['NORMAL', 'OVERHEAT', 'LOW_BATTERY'] as VehicleScenario[]).map((item) => (
                <Pressable key={item} onPress={() => chooseScenario(item)} style={[styles.scenario, scenario === item && styles.scenarioActive]}>
                  <Text style={[styles.scenarioText, scenario === item && styles.scenarioTextActive]}>{item === 'NORMAL' ? 'Normal' : item === 'OVERHEAT' ? 'Superaquecimento' : 'Bateria baixa'}</Text>
                </Pressable>
              ))}
            </View>

            <Pressable style={[styles.simulationButton, localRunning && styles.simulationButtonActive]} onPress={toggleLocalSource}>
              <View style={[styles.simDot, localRunning && styles.simDotActive]} />
              <Text style={[styles.simulationText, localRunning && styles.simulationTextActive]}>{localRunning ? 'Parar ECU simulada' : 'Iniciar ECU simulada'}</Text>
            </Pressable>
            <Text style={styles.queueStatus}>{queuedSamples} na fila · último lote aceito: {lastBatchSize} · fonte: {dataSource.current.name}</Text>

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

            <SectionTitle kicker="LEITURA OBD" title="Diagnósticos ativos" count={data.activeDiagnostics.length} />
            <View style={styles.card}>
              {!data.activeDiagnostics.length ? <Empty title="Nenhuma falha ativa" text="A leitura contínua não encontrou códigos." /> : data.activeDiagnostics.map((diagnostic) => (
                <View style={styles.diagnosticRow} key={diagnostic.id}>
                  <Text style={styles.code}>{diagnostic.code}</Text>
                  <View style={styles.alertCopy}><Text style={styles.alertTitle}>{diagnostic.description}</Text><Text style={styles.alertMessage}>{diagnostic.severity}</Text></View>
                </View>
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

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#07100D' },
  content: { paddingHorizontal: 20, paddingTop: 18, paddingBottom: 48 },
  loading: { flex: 1, backgroundColor: '#07100D', alignItems: 'center', justifyContent: 'center', gap: 22, padding: 30 },
  muted: { color: '#687B72', fontSize: 11, textAlign: 'center' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 34 },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  brandMark: { width: 36, height: 36, borderWidth: 1, borderColor: '#54F0A5', borderTopLeftRadius: 11, borderBottomRightRadius: 11, alignItems: 'center', justifyContent: 'center' },
  brandLetter: { color: '#54F0A5', fontSize: 20, fontWeight: '800', fontStyle: 'italic' },
  brandName: { color: '#F0F9F5', fontSize: 21, fontWeight: '800', letterSpacing: -1 },
  brandTag: { color: '#53675E', fontSize: 6, fontWeight: '700', letterSpacing: 1.3 },
  online: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 14, backgroundColor: '#48EFA012' },
  onlineDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#48EFA0' },
  onlineText: { color: '#6ADAA9', fontSize: 8, fontWeight: '800', letterSpacing: 1 },
  eyebrow: { color: '#687B72', fontSize: 8, fontWeight: '800', letterSpacing: 1.7 },
  title: { color: '#F2F9F6', fontSize: 31, fontWeight: '800', letterSpacing: -1.2, marginTop: 5 },
  subtitle: { color: '#788B82', fontSize: 13, marginTop: 3, marginBottom: 20 },
  vehicleTabs: { marginBottom: 14, flexGrow: 0 },
  vehicleTab: { marginRight: 8, paddingHorizontal: 13, paddingVertical: 8, borderRadius: 10, backgroundColor: '#111D18' },
  vehicleTabActive: { borderWidth: 1, borderColor: '#48EFA0' },
  vehicleTabText: { color: '#B8C9C1', fontSize: 10, fontWeight: '700' },
  heroCard: { minHeight: 155, padding: 22, borderRadius: 19, borderWidth: 1, borderColor: '#C2FFE20F', backgroundColor: '#12211B', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', overflow: 'hidden' },
  cardLabel: { color: '#6E8178', fontSize: 8, fontWeight: '800', letterSpacing: 1.5 },
  healthLine: { flexDirection: 'row', alignItems: 'flex-end', marginTop: 10 },
  healthValue: { color: '#54EFA4', fontSize: 50, lineHeight: 54, fontWeight: '800', letterSpacing: -2 },
  healthUnit: { color: '#577066', fontSize: 11, marginBottom: 8, marginLeft: 3 },
  healthCaption: { color: '#AFC2B9', fontSize: 11, fontWeight: '600' },
  carOrb: { width: 95, height: 95, borderRadius: 48, backgroundColor: '#42E99A13', borderWidth: 1, borderColor: '#57EFA326', alignItems: 'center', justifyContent: 'center' },
  carGlyph: { color: '#56EFA5', fontSize: 58, transform: [{ rotate: '-8deg' }] },
  metrics: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 10 },
  metric: { width: '48.5%', minHeight: 107, padding: 15, borderRadius: 15, backgroundColor: '#101C17', borderWidth: 1, borderColor: '#FFFFFF0B', overflow: 'hidden' },
  metricLabel: { color: '#65786F', fontSize: 8, fontWeight: '800', letterSpacing: 1 },
  metricLine: { flexDirection: 'row', alignItems: 'flex-end', marginTop: 13 },
  metricValue: { fontSize: 27, lineHeight: 31, fontWeight: '800', letterSpacing: -1 },
  metricUnit: { color: '#64776E', fontSize: 8, marginLeft: 4, marginBottom: 4 },
  metricBar: { position: 'absolute', width: 24, height: 3, borderRadius: 2, right: 14, bottom: 14 },
  scenarioLabel: { marginTop: 18, marginBottom: 8 },
  scenarios: { flexDirection: 'row', gap: 6 },
  scenario: { flex: 1, minHeight: 42, paddingHorizontal: 6, borderRadius: 11, borderWidth: 1, borderColor: '#2B4238', alignItems: 'center', justifyContent: 'center', backgroundColor: '#101B17' },
  scenarioActive: { borderColor: '#48EFA0', backgroundColor: '#48EFA018' },
  scenarioText: { color: '#71847B', fontSize: 8, fontWeight: '700', textAlign: 'center' },
  scenarioTextActive: { color: '#63E8AB' },
  simulationButton: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 9, marginTop: 11, padding: 15, borderRadius: 14, borderWidth: 1, borderColor: '#365247', backgroundColor: '#14221C' },
  simulationButtonActive: { backgroundColor: '#48EFA0', borderColor: '#48EFA0' },
  simulationText: { color: '#AFC2B9', fontSize: 11, fontWeight: '700' },
  simulationTextActive: { color: '#08120E' },
  simDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: '#72867C' },
  simDotActive: { backgroundColor: '#08120E' },
  queueStatus: { color: '#52675D', fontSize: 8, textAlign: 'center', marginTop: 7 },
  sectionTitle: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 30, marginBottom: 11 },
  sectionName: { color: '#ECF6F1', fontSize: 18, fontWeight: '800', letterSpacing: -.5, marginTop: 3 },
  count: { minWidth: 28, height: 28, paddingHorizontal: 8, borderRadius: 9, backgroundColor: '#FF75521A', alignItems: 'center', justifyContent: 'center' },
  countText: { color: '#FF977C', fontSize: 11, fontWeight: '800' },
  card: { padding: 10, borderRadius: 17, borderWidth: 1, borderColor: '#C2FFE20D', backgroundColor: '#101B17' },
  alertRow: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 10, borderRadius: 12, backgroundColor: '#0B1511', marginVertical: 4 },
  alertMark: { width: 33, height: 33, borderRadius: 9, backgroundColor: '#FFB85C16', alignItems: 'center', justifyContent: 'center' },
  alertMarkCritical: { backgroundColor: '#FF676719' },
  alertBang: { color: '#FFB85C', fontSize: 15, fontWeight: '800' },
  alertCopy: { flex: 1 },
  alertTitle: { color: '#D6E5DE', fontSize: 10, fontWeight: '700' },
  alertMessage: { color: '#667970', fontSize: 9, marginTop: 3 },
  ack: { width: 29, height: 29, borderRadius: 9, borderWidth: 1, borderColor: '#315044', alignItems: 'center', justifyContent: 'center' },
  ackText: { color: '#61DDA8', fontWeight: '800' },
  diagnosticRow: { flexDirection: 'row', alignItems: 'center', gap: 11, padding: 11, marginVertical: 3 },
  code: { color: '#65E5AC', backgroundColor: '#49EEA014', paddingHorizontal: 8, paddingVertical: 6, borderRadius: 7, fontSize: 10, fontWeight: '800' },
  empty: { alignItems: 'center', justifyContent: 'center', paddingVertical: 26, paddingHorizontal: 15 },
  emptyMark: { width: 40, height: 40, borderRadius: 20, borderWidth: 1, borderColor: '#2D4A3E', alignItems: 'center', justifyContent: 'center' },
  emptyCheck: { color: '#54EFA4', fontSize: 16, fontWeight: '800' },
  emptyTitle: { color: '#B2C3BB', fontSize: 11, fontWeight: '700', marginTop: 10 },
  emptyText: { color: '#64766E', fontSize: 9, marginTop: 3, textAlign: 'center' },
  error: { padding: 13, borderRadius: 12, backgroundColor: '#FF626218', borderWidth: 1, borderColor: '#FF62622D', marginBottom: 18 },
  errorText: { color: '#FFB1B1', fontSize: 11, fontWeight: '700' },
  errorHint: { color: '#A86F6F', fontSize: 8, marginTop: 4 },
  footer: { color: '#45584F', fontSize: 8, textAlign: 'center', marginTop: 28 },
});
