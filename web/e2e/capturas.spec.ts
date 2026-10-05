import { Page, expect, test } from '@playwright/test';

/**
 * Genera las capturas de docs/capturas/ (no es un test). Se corre a mano:
 *   CAPTURAS=1 npx playwright test capturas
 */
test.skip(!process.env['CAPTURAS'], 'Sólo para regenerar las capturas del README');
test.use({ viewport: { width: 1280, height: 800 } });

const DESTINO = '../docs/capturas';

async function entrar(page: Page, usuario: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Usuario').fill(usuario);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}

test('capturas del cliente', async ({ page }) => {
  await page.goto('/login');
  await page.getByText('Usuarios de prueba').click();
  await page.screenshot({ path: `${DESTINO}/04-web-login.png` });

  await entrar(page, 'ana', 'ana123');
  await expect(page.getByRole('heading', { name: 'Mis cuentas' })).toBeVisible();
  await page.screenshot({ path: `${DESTINO}/05-web-mis-cuentas.png` });

  await page.getByRole('link', { name: 'Transferir' }).first().click();
  await page.getByLabel('Cuenta de origen').selectOption({ index: 1 });
  await page.getByLabel('CBU de destino').fill('9990001800000000010025');
  await page.getByLabel('Importe').fill('12500');
  await page.getByLabel('Concepto (opcional)').fill('Alquiler octubre');
  await page.getByRole('button', { name: 'Continuar' }).click();
  await expect(page.getByRole('heading', { name: 'Revisá los datos' })).toBeVisible();
  await page.screenshot({ path: `${DESTINO}/06-web-confirmar-transferencia.png` });
  await page.getByRole('button', { name: 'Confirmar transferencia' }).click();
  await page.getByRole('link', { name: 'Ver movimientos' }).click();
  await expect(page.getByRole('cell', { name: /Alquiler octubre/ }).first()).toBeVisible();
  await page.screenshot({ path: `${DESTINO}/07-web-movimientos.png` });
});

test('captura del operador', async ({ page }) => {
  await entrar(page, 'operador', 'operador123');
  await expect(page.getByRole('heading', { name: 'Cuentas' })).toBeVisible();
  await page.getByRole('row').nth(1).waitFor();
  await page.screenshot({ path: `${DESTINO}/08-web-operador.png`, fullPage: true });
});
