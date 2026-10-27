const { test, expect } = require('@playwright/test');
const { createProduct, loginAsAdmin } = require('./helpers');

test('dashboard refleja la API y cambios recientes de inventario', async ({ page }) => {
  await loginAsAdmin(page);
  const apiSnapshot = await page.evaluate(async () => {
    const [productsResponse, salesResponse, cashResponse] = await Promise.all([
      fetch('/api/productos'),
      fetch('/api/ventas'),
      fetch('/api/caja')
    ]);
    const [products, sales, cash] = await Promise.all([
      productsResponse.json(),
      salesResponse.json(),
      cashResponse.json()
    ]);
    return {
      activeProducts: products.filter((product) => product.active).length,
      issuedSales: sales.filter((sale) => sale.status !== 'Anulado').length,
      income: Number(cash.income || 0),
      net: Number(cash.net || 0)
    };
  });

  const productName = `E2E Dashboard ${Date.now()}`;
  await createProduct(page, productName);
  await page.goto('/dashboard.html');

  await expect(page.locator('#todaySales')).toHaveText(`S/. ${apiSnapshot.income.toFixed(2)}`);
  await expect(page.locator('#todayNet')).toHaveText(`S/. ${apiSnapshot.net.toFixed(2)}`);
  await expect(page.locator('#issuedSales')).toHaveText(String(apiSnapshot.issuedSales));
  await expect(page.locator('#activeProducts')).toHaveText(String(apiSnapshot.activeProducts + 1));
  await expect(page.locator('#weeklySalesChart .bar-group')).toHaveCount(7);
});