import { expect, test } from '@playwright/test';

// CBU de la caja de ahorro en pesos de Bruno (datos demo).
const CBU_BRUNO_ARS = '9990001800000000010025';

/**
 * Recorrido completo de un cliente contra la app real (nginx + API + PostgreSQL + Redis):
 * login → mis cuentas → transferencia con confirmación → comprobante → movimientos → salir.
 */
test('un cliente transfiere a otra cuenta y lo ve en sus movimientos', async ({ page }) => {
  const concepto = `E2E ${Date.now()}`;

  await page.goto('/');
  await expect(page).toHaveURL(/\/login/);
  await page.getByLabel('Usuario').fill('ana');
  await page.getByLabel('Contraseña').fill('ana123');
  await page.getByRole('button', { name: 'Ingresar' }).click();

  await expect(page.getByRole('heading', { name: 'Mis cuentas' })).toBeVisible();
  const tarjetaPesos = page.locator('article').filter({ hasText: 'Caja de ahorro en ARS' });
  const saldoAntes = await tarjetaPesos.getByTestId('saldo').innerText();

  await tarjetaPesos.getByRole('link', { name: 'Transferir' }).click();
  await page.getByLabel('CBU de destino').fill(CBU_BRUNO_ARS);
  await page.getByLabel('Importe').fill('1500');
  await page.getByLabel('Concepto (opcional)').fill(concepto);
  await page.getByRole('button', { name: 'Continuar' }).click();

  // Paso de confirmación: los datos se muestran antes de mover plata.
  await expect(page.getByRole('heading', { name: 'Revisá los datos' })).toBeVisible();
  await expect(page.getByText(CBU_BRUNO_ARS)).toBeVisible();
  await page.getByRole('button', { name: 'Confirmar transferencia' }).click();

  await expect(page.getByRole('heading', { name: 'Transferencia realizada' })).toBeVisible();
  await page.getByRole('link', { name: 'Ver movimientos' }).click();
  await expect(page.getByRole('cell', { name: new RegExp(concepto) })).toBeVisible();

  // El saldo de "Mis cuentas" refleja la transferencia (el caché de Redis se invalidó).
  await page.getByRole('link', { name: 'Mis cuentas' }).click();
  await expect(tarjetaPesos.getByTestId('saldo')).not.toHaveText(saldoAntes);

  await page.getByRole('button', { name: 'Salir' }).click();
  await expect(page).toHaveURL(/\/login/);
});
