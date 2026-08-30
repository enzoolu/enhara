import { expect, test } from '@playwright/test'

test('carrega snapshot e recebe o cenário crítico por SSE', async ({ page, request }) => {
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))

  const vehiclesResponse = await request.get('http://127.0.0.1:8080/api/vehicles')
  const vehicles = await vehiclesResponse.json() as Array<{ id: string }>
  const vehicleId = vehicles[0].id
  const dashboardResponse = await request.get(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/dashboard`)
  const dashboard = await dashboardResponse.json() as { openAlerts: Array<{ id: string }> }
  for (const alert of dashboard.openAlerts) {
    await request.patch(`http://127.0.0.1:8080/api/alerts/${alert.id}/acknowledge`)
  }
  await request.post(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/simulation/scenario/NORMAL`)
  await request.post(`http://127.0.0.1:8080/api/vehicles/${vehicleId}/simulation/tick`)

  await page.goto('/')
  await expect(page.getByText('Enhara Demo Car', { exact: true })).toBeVisible({ timeout: 12_000 })
  await expect(page.getByText('API conectada')).toBeVisible()
  await expect(page.getByText('Nenhum alerta aberto')).toBeVisible()

  await page.getByRole('button', { name: 'Superaquecimento' }).click()
  await expect(page.getByRole('button', { name: 'Superaquecimento' })).toHaveClass(/active/)
  await expect(page.getByText('Temperatura do motor elevada')).toBeVisible({ timeout: 22_000 })
  await expect(page.getByText('ENGINE_TEMPERATURE_HIGH')).toBeVisible()
  await expect(page.getByText('Situação crítica', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Simulação ativa' }).click()
  await expect(page.getByText('Score experimental').first()).toBeVisible()

  expect(pageErrors).toEqual([])
  await page.screenshot({ path: 'docs/cp1/dashboard.png', fullPage: true })
})
