import { expect, test } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'

async function prepareNormalScenario(request: APIRequestContext) {
  const vehiclesResponse = await request.get('http://127.0.0.1:8080/api/vehicles')
  const vehicles = await vehiclesResponse.json() as Array<{ id: string }>
  const vehicleId = vehicles[0].id
  const dashboardResponse = await request.get(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/dashboard`)
  const dashboard = await dashboardResponse.json() as { openAlerts: Array<{ id: string }> }
  for (const alert of dashboard.openAlerts) {
    await request.patch(`http://127.0.0.1:8080/api/alerts/${alert.id}/acknowledge`)
  }
  const notesResponse = await request.get(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/notes?includeCompleted=true`)
  const notes = await notesResponse.json() as Array<{ id: string }>
  for (const note of notes) {
    await request.delete(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/notes/${note.id}`)
  }
  const photosResponse = await request.get(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/photos`)
  const photos = await photosResponse.json() as Array<{ id: string }>
  for (const photo of photos) {
    await request.delete(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/photos/${photo.id}`)
  }
  await request.post(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/simulation/scenario/NORMAL`)
  await request.post(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/simulation/tick`)
  await request.post(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/simulation/start`)
  return vehicleId
}

test('painel e estatísticas preservam as origens técnicas no fluxo ao vivo', async ({ page, request }) => {
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  const vehicleId = await prepareNormalScenario(request)

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Seu carro, com clareza.' })).toBeVisible({ timeout: 12_000 })
  await expect(page.getByText('API conectada').first()).toBeVisible()
  await expect(page.getByText('Estado atual', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('Qualidade do pneu')).toHaveCount(0)
  await expect(page.getByText('fluido de freio', { exact: false })).toHaveCount(0)

  await page.getByRole('button', { name: 'Superaquecimento' }).click()
  await expect(page.getByRole('button', { name: 'Superaquecimento' })).toHaveClass(/active/)
  for (let index = 0; index < 38; index++) {
    await request.post(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/simulation/tick`)
  }
  await expect(page.getByText('Temperatura do motor elevada')).toBeVisible({ timeout: 22_000 })
  await expect(page.getByText('ENGINE_TEMPERATURE_HIGH')).toBeVisible()
  await expect(page.getByText('Situação crítica', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Tensão baixa', exact: true }).click()
  for (let index = 0; index < 10; index++) {
    await request.post(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/simulation/tick`)
  }
  await expect(page.getByText('BATTERY_VOLTAGE_LOW')).toBeVisible()
  await page.getByRole('button', { name: 'Normal', exact: true }).click()
  for (let index = 0; index < 12; index++) {
    await request.post(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/simulation/tick`)
  }
  await expect(page.getByText('BATTERY_VOLTAGE_LOW')).toHaveCount(0)

  await request.post(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/simulation/scenario/MISFIRE`)
  for (let index = 0; index < 6; index++) {
    await request.post(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/simulation/tick`)
  }

  await page.getByRole('button', { name: 'ECU conectada' }).click()
  await expect(page.getByText('Última leitura válida', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('Máxima registrada', { exact: true }).first()).toBeVisible()
  await page.screenshot({ path: 'docs/cp1/dashboard.png', fullPage: true })

  await page.getByRole('button', { name: /Minhas estatísticas/ }).click()
  await expect(page.getByRole('heading', { name: 'Minhas estatísticas' })).toBeVisible()
  await expect(page.getByText('Consumo médio')).toBeVisible()
  await expect(page.getByText('Falta combustível consumido confiável')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Parâmetros disponíveis' })).toBeVisible()
  await expect(page.getByText('P0300').first()).toBeVisible()
  await expect(page.getByText('Explicação amigável')).toBeVisible()
  await expect(page.getByText('Primeira ocorrência')).toBeVisible()
  await expect(page.getByText('Última ocorrência')).toBeVisible()
  await expect(page.getByText('Freeze frame')).toBeVisible()
  await expect(page.getByText('Não suportado').first()).toBeVisible()
  await expect(page.getByText('Ainda não descoberto').first()).toBeVisible()

  await page.getByRole('button', { name: /Velocidade.*Último dado.*antigo/i }).click()
  await expect(page.getByText('Histórico registrado', { exact: true })).toBeVisible()
  await expect(page.getByText(/Origem: ECU\/OBD simulada/)).toBeVisible()
  await page.getByRole('button', { name: 'Fechar detalhes' }).click()

  await page.getByRole('button', { name: '+ Nova nota' }).click()
  await page.getByLabel('Título').fill('Verificar manual do veículo')
  await page.getByLabel('Descrição').fill('Confirmar o intervalo de manutenção indicado pelo fabricante.')
  await page.getByRole('button', { name: 'Adicionar nota' }).click()
  await expect(page.getByText('Verificar manual do veículo').first()).toBeVisible()
  await expect(page.getByText('Informado pelo usuário').first()).toBeVisible()
  await expect(page.getByText('PENDENTE', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Concluir nota' }).click()
  await expect(page.getByText('CONCLUÍDA', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Reabrir nota' }).click()
  await expect(page.getByText('PENDENTE', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Editar' }).click()
  await page.getByLabel('Título').fill('Verificar manual e garantia')
  await page.getByRole('button', { name: 'Salvar alterações' }).click()
  await expect(page.getByRole('heading', { name: 'Verificar manual e garantia', exact: true })).toBeVisible()

  expect(pageErrors).toEqual([])
  await page.screenshot({ path: 'docs/cp1/statistics.png', fullPage: true })

  page.once('dialog', (dialog) => void dialog.accept())
  await page.getByRole('button', { name: 'Excluir' }).click()
  await expect(page.getByText('Verificar manual e garantia')).toHaveCount(0)
})

test('painel e estatísticas não criam overflow em viewport móvel', async ({ page, request }) => {
  await prepareNormalScenario(request)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')

  await expect(page.getByRole('navigation', { name: 'Navegação móvel' })).toBeVisible({ timeout: 12_000 })
  await expect(page.getByRole('heading', { name: 'Seu carro, com clareza.' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await page.getByRole('navigation', { name: 'Navegação móvel' }).getByRole('button', { name: /Estatísticas/ }).click()
  await expect(page.getByRole('heading', { name: 'Minhas estatísticas' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Parâmetros disponíveis' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('Meu Carro persiste correção manual e foto sem usar dados simulados', async ({ page, request }) => {
  await prepareNormalScenario(request)
  const version = `Versão confirmada E2E ${Date.now()}`
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  await page.goto('/')

  await page.getByRole('navigation', { name: 'Navegação principal' })
    .getByRole('button', { name: /Meu carro/ }).click()
  await expect(page.getByRole('heading', { name: 'Meu carro' })).toBeVisible({ timeout: 12_000 })
  await expect(page.getByText('Sem dados do simulador')).toBeVisible()
  await expect(page.getByText('Identificação e especificações')).toBeVisible()
  await expect(page.getByText(/Última atualização externa:/)).toBeVisible()
  await expect(page.getByText('Potência', { exact: true })).toHaveCount(0)
  await expect(page.getByText('Torque', { exact: true })).toHaveCount(0)

  await page.getByRole('button', { name: 'Corrigir dados' }).click()
  await page.getByLabel('Versão').fill(version)
  await page.getByLabel('Combustível').fill('Flex informado pelo usuário')
  await page.getByRole('button', { name: 'Salvar alterações' }).click()
  await expect(page.getByText(version, { exact: true })).toBeVisible()
  await expect(page.getByText('Informado pelo usuário').first()).toBeVisible()

  await page.getByLabel('Foto JPEG ou PNG').setInputFiles({
    name: 'meu-carro.png',
    mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64'),
  })
  await page.getByLabel('Legenda').fill('Foto persistida E2E')
  await page.getByRole('button', { name: 'Adicionar foto' }).click()
  await expect(page.getByText('Foto persistida E2E')).toBeVisible()

  await page.reload()
  await page.getByRole('navigation', { name: 'Navegação principal' })
    .getByRole('button', { name: /Meu carro/ }).click()
  await expect(page.getByText(version, { exact: true })).toBeVisible({ timeout: 12_000 })
  await expect(page.getByText('Foto persistida E2E')).toBeVisible()
  expect(pageErrors).toEqual([])
  await page.screenshot({ path: 'docs/cp1/my-car.png', fullPage: true })
})
