require('dotenv').config();

const fs = require('fs');
const mysql = require('mysql2/promise');

async function prepareDatabase() {
  const databaseName = process.env.E2E_DB_NAME || 'ingearq_e2e';
  if (!/^[a-zA-Z0-9_]+$/.test(databaseName)) {
    throw new Error('E2E_DB_NAME solo puede contener letras, numeros y guiones bajos.');
  }

  const connection = await mysql.createConnection({
    host: process.env.DB_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    multipleStatements: true
  });

  try {
    await connection.query(
      `CREATE DATABASE IF NOT EXISTS ${databaseName} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
    );
    await connection.query(`USE ${databaseName}`);

    const schema = fs.readFileSync('database/ingearq2026.sql', 'utf8')
      .replace(/CREATE DATABASE IF NOT EXISTS `ingearq2026`[\s\S]*?;\s*/i, '')
      .replace(/USE `ingearq2026`;\s*/i, '');
    await connection.query(schema);
    console.log(`Esquema E2E preparado: ${databaseName}`);
  } finally {
    await connection.end();
  }
}

prepareDatabase().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});