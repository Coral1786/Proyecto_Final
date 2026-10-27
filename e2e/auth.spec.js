const { test, expect } = require('@playwright/test');

test('rechaza credenciales inválidas y permite iniciar sesión con el administrador', async ({ page }) => {
  await page.goto('/');
  await page.locator('#usuario').fill(process.env.E2E_ADMIN_USER || 'admin');
  await page.locator('#password').fill('invalid-e2e-password');
  await page.getByRole('button', { name: 'Ingresar al panel' }).click();
  await expect(page.locator('#loginError')).toContainText('incorrectos');

  await page.locator('#password').fill(
    process.env.DEMO_ADMIN_PASSWORD || process.env.E2E_ADMIN_PASSWORD || 'Ingearq2024!'
  );
  await page.getByRole('button', { name: 'Ingresar al panel' }).click();
  await expect(page).toHaveURL(/dashboard\.html$/);
  await expect(page.getByRole('heading', { name: 'Panel de control' })).toBeVisible();
});