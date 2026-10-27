require('dotenv').config();

const { expect } = require('@playwright/test');

async function loginAsAdmin(page) {
  await page.goto('/');
  await page.locator('#usuario').fill(process.env.E2E_ADMIN_USER || 'admin');
  await page.locator('#password').fill(
    process.env.DEMO_ADMIN_PASSWORD || process.env.E2E_ADMIN_PASSWORD || 'Ingearq2024!'
  );
  await page.getByRole('button', { name: 'Ingresar al panel' }).click();
  await expect(page).toHaveURL(/dashboard\.html$/);
}

async function createProduct(page, name, stock = 5, price = 10) {
  await page.goto('/product_manager.html');
  await page.locator('#newProductBtn').click();
  await page.getByLabel('Nombre').fill(name);
  await page.getByLabel('Categoría').selectOption('Materiales');
  await page.getByLabel('Descripción breve').fill('Producto temporal de pruebas');
  await page.getByLabel('Stock').fill(String(stock));
  await page.getByLabel('Precio').fill(String(price));
  await page.getByRole('button', { name: 'Guardar producto' }).click();

  const productRow = page.locator('#productTableBody tr').filter({ hasText: name });
  await expect(productRow).toBeVisible();
  return productRow;
}

module.exports = { createProduct, loginAsAdmin };