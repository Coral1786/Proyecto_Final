const { test, expect } = require('@playwright/test');
const { createProduct, loginAsAdmin } = require('./helpers');

test('administra un producto: alta, edición, baja y reactivación', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());
  await loginAsAdmin(page);

  const productName = `E2E Producto ${Date.now()}`;
  let productRow = await createProduct(page, productName);
  await expect(productRow).toContainText('Materiales');
  await expect(productRow).toContainText('S/. 10.00');
  await expect(productRow).toContainText('5');

  await productRow.getByRole('button', { name: 'Editar' }).click();
  const updatedName = `${productName} Editado`;
  await page.getByLabel('Nombre').fill(updatedName);
  await page.getByLabel('Stock').fill('7');
  await page.getByRole('button', { name: 'Guardar producto' }).click();
  productRow = page.locator('#productTableBody tr').filter({ hasText: updatedName });
  await expect(productRow).toContainText('7');

  await productRow.getByRole('button', { name: 'Dar de baja' }).click();
  await expect(productRow).toContainText('Dado de baja');
  await productRow.getByRole('button', { name: 'Reactivar' }).click();
  await expect(productRow).toContainText('Poco stock');

  await productRow.getByRole('button', { name: 'Dar de baja' }).click();
  await expect(productRow).toContainText('Dado de baja');
});