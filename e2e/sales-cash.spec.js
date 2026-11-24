const { test, expect } = require('@playwright/test');
const { createProduct, loginAsAdmin } = require('./helpers');

test('vende, edita y anula un comprobante; verifica stock y caja', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());
  await page.addInitScript(() => {
    window.__printCalls = 0;
    window.print = () => {
      window.__printCalls += 1;
    };
  });
  await loginAsAdmin(page);

  await page.goto('/cash_details.html');
  const initialBalance = Number((await page.locator('#cashBalance').innerText()).replace('S/.', '').trim());
  const productName = `E2E Venta ${Date.now()}`;
  const productRow = await createProduct(page, productName, 5, 10);
  const customer = `Cliente E2E ${Date.now()}`;

  await page.goto('/sale_register.html');
  await page.getByLabel('Cliente').fill(customer);
  await page.locator('#productSearch').fill(productName);
  const productResult = page.locator('#productResults button[data-product-id]').first();
  await expect(productResult).toBeVisible();
  await productResult.click();
  await page.locator('#saleItemsBody .qty-input').fill('2');
  await expect(page.locator('#totalValue')).toContainText('23.60');
  const cashReceived = page.getByLabel('Monto recibido (efectivo)');
  await cashReceived.fill('20');
  await expect(cashReceived).toHaveJSProperty('validationMessage', 'El monto recibido debe cubrir el total de la venta.');
  await cashReceived.fill('25');
  await expect(page.locator('#cashChangeValue')).toHaveText('S/. 1.40');

  const saleResponse = page.waitForResponse((response) =>
    response.url().endsWith('/api/ventas') && response.request().method() === 'POST'
  );
  await page.getByRole('button', { name: 'Cobrar y guardar comprobante' }).click();
  const createdSale = await (await saleResponse).json();
  expect(createdSale.total).toBe(23.6);
  expect(createdSale.amountReceived).toBe(25);
  expect(createdSale.changeDue).toBe(1.4);
  await expect(page.locator('#receipt')).toContainText('Recibido: S/. 25.00');
  await expect(page.locator('#receipt')).toContainText('Vuelto: S/. 1.40');
  await expect.poll(() => page.evaluate(() => window.__printCalls)).toBe(1);
  await page.emulateMedia({ media: 'print' });
  await expect(page.locator('#receipt')).toBeVisible();
  const pdf = await page.pdf({ printBackground: true });
  expect(pdf.subarray(0, 4).toString()).toBe('%PDF');
  await page.emulateMedia({ media: 'screen' });
  await page.getByRole('button', { name: 'Imprimir / Guardar PDF' }).click();
  await expect.poll(() => page.evaluate(() => window.__printCalls)).toBe(2);
  await page.getByRole('button', { name: 'Nueva venta' }).click();
  await expect(page.locator('.sales-layout')).toBeVisible();

  await page.goto('/sales_details.html');
  let saleRow = page.locator('#salesTableBody tr').filter({ hasText: customer });
  await expect(saleRow).toContainText('S/. 23.60');
  await saleRow.getByRole('button', { name: 'Editar' }).click();
  const updatedCustomer = `${customer} Editado`;
  await page.getByLabel('Cliente').fill(updatedCustomer);
  await page.locator('#editSaleType').selectOption('Factura');
  await page.getByRole('button', { name: 'Guardar cambios' }).click();
  saleRow = page.locator('#salesTableBody tr').filter({ hasText: updatedCustomer });
  await expect(saleRow).toContainText('Factura');
  await saleRow.getByRole('button', { name: 'Reimprimir' }).click();
  await expect(page.locator('#receipt')).toContainText('Factura');
  await expect.poll(() => page.evaluate(() => window.__printCalls)).toBe(1);
  await page.emulateMedia({ media: 'print' });
  const invoicePdf = await page.pdf({ printBackground: true });
  expect(invoicePdf.subarray(0, 4).toString()).toBe('%PDF');
  await page.emulateMedia({ media: 'screen' });

  const cancelResponse = page.waitForResponse((response) =>
    response.url().endsWith(`/api/ventas/${createdSale.id}`) && response.request().method() === 'DELETE'
  );
  await saleRow.getByRole('button', { name: 'Anular' }).click();
  expect((await cancelResponse).status()).toBe(204);
  await expect(saleRow).toContainText('Anulado');

  await page.goto('/product_manager.html');
  await page.locator('#productSearch').fill(productName);
  await expect(productRow.locator('td').nth(2)).toHaveText('5');
  await productRow.getByRole('button', { name: 'Dar de baja' }).click();

  await page.goto('/cash_details.html');
  await expect(page.locator('#expenseList')).toContainText(`Anulación INV-${createdSale.id}`);
  const balanceAfterCancellation = Number((await page.locator('#cashBalance').innerText()).replace('S/.', '').trim());
  expect(balanceAfterCancellation).toBe(initialBalance);

  const expenseDescription = `E2E egreso ${Date.now()}`;
  await page.locator('#addExpenseBtn').click();
  await page.getByLabel('Descripción').fill(expenseDescription);
  await page.getByLabel('Monto').fill('2.50');
  await page.getByRole('button', { name: 'Guardar egreso' }).click();
  await expect(page.locator('#expenseList')).toContainText(expenseDescription);
  await expect(page.locator('#cashBalance')).toContainText(`S/. ${(initialBalance - 2.5).toFixed(2)}`);
});