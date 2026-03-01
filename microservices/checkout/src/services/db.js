const { Pool } = require('pg');
const { logger } = require('../config/logger');

const pool = new Pool({
  host:     process.env.DB_HOST     || 'checkout-db',
  port:     process.env.DB_PORT     || 5432,
  database: process.env.DB_NAME     || 'guitarshop_checkout',
  user:     process.env.DB_USER     || 'guitarshop',
  password: process.env.DB_PASSWORD || 'guitarshop123',
  max: 10,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 5000,
});

async function initDB() {
  const maxRetries = 10;
  for (let i = 0; i < maxRetries; i++) {
    try {
      await pool.query('SELECT 1');
      logger.info('✅ Connected to checkout DB');
      break;
    } catch (err) {
      logger.warn(`⏳ Waiting for DB... attempt ${i + 1}/${maxRetries}`);
      await new Promise(r => setTimeout(r, 3000));
      if (i === maxRetries - 1) {
        logger.error('❌ Could not connect to checkout DB');
        process.exit(1);
      }
    }
  }

  await pool.query(`
    CREATE TABLE IF NOT EXISTS checkouts (
      id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      customer_id   VARCHAR(100) NOT NULL,
      email         VARCHAR(255) NOT NULL,
      first_name    VARCHAR(100),
      last_name     VARCHAR(100),
      address       TEXT,
      city          VARCHAR(100),
      country       VARCHAR(100),
      postal_code   VARCHAR(20),
      items         JSONB NOT NULL DEFAULT '[]',
      subtotal      DECIMAL(10,2),
      shipping_cost DECIMAL(10,2) DEFAULT 9.99,
      total         DECIMAL(10,2),
      status        VARCHAR(50) DEFAULT 'PENDING',
      created_at    TIMESTAMPTZ DEFAULT NOW(),
      updated_at    TIMESTAMPTZ DEFAULT NOW()
    )
  `);
  logger.info('✅ Checkout table ready');
}

module.exports = { pool, initDB };
