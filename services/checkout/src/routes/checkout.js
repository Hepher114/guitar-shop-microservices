const express = require('express');
const { v4: uuidv4 } = require('uuid');
const { pool, initDB } = require('../services/db');
const { publishOrderCreated } = require('../services/messaging');
const { logger } = require('../config/logger');

const router = express.Router();

// Initialize DB on startup
initDB();

// ─── POST /checkout  — create a checkout session ──────────────────────────────
router.post('/', async (req, res) => {
  const { customerId, email, firstName, lastName, address, city, country, postalCode, items } = req.body;

  if (!customerId || !email || !items || items.length === 0) {
    return res.status(400).json({ error: 'customerId, email, and items are required' });
  }

  const subtotal     = items.reduce((acc, item) => acc + item.price * item.quantity, 0);
  const shippingCost = subtotal >= 100 ? 0 : 9.99; // free shipping over $100
  const total        = subtotal + shippingCost;

  try {
    const result = await pool.query(
      `INSERT INTO checkouts
         (id, customer_id, email, first_name, last_name, address, city, country, postal_code, items, subtotal, shipping_cost, total, status)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,'PENDING')
       RETURNING *`,
      [uuidv4(), customerId, email, firstName, lastName, address, city, country, postalCode,
       JSON.stringify(items), subtotal.toFixed(2), shippingCost.toFixed(2), total.toFixed(2)]
    );

    const checkout = result.rows[0];
    await publishOrderCreated(checkout);

    logger.info(`✅ Checkout created: ${checkout.id}`);
    res.status(201).json(checkout);
  } catch (err) {
    logger.error('Failed to create checkout:', err);
    res.status(500).json({ error: 'Failed to create checkout' });
  }
});

// ─── GET /checkout/:id ────────────────────────────────────────────────────────
router.get('/:id', async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM checkouts WHERE id = $1', [req.params.id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Checkout not found' });
    }
    res.json(result.rows[0]);
  } catch (err) {
    logger.error('Failed to get checkout:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// ─── GET /checkout/customer/:customerId — list customer checkouts ──────────────
router.get('/customer/:customerId', async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT * FROM checkouts WHERE customer_id = $1 ORDER BY created_at DESC LIMIT 20',
      [req.params.customerId]
    );
    res.json(result.rows);
  } catch (err) {
    logger.error('Failed to list checkouts:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;
