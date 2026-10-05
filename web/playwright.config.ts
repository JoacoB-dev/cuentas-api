import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra el stack completo levantado con Docker Compose (web + API + Postgres + Redis).
 *   docker compose up -d --build --wait   (desde la raíz del repo)
 *   cd web && npx playwright test
 * Otra URL: E2E_BASE_URL=http://localhost:4200 npx playwright test (con ng serve + API local).
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4201',
    locale: 'es-AR',
    timezoneId: 'America/Argentina/Buenos_Aires',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
