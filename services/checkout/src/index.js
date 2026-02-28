const express = require('express');
const morgan  = require('morgan');
const { logger } = require('./config/logger');
const checkoutRoutes = require('./routes/checkout');

const app  = express();
const PORT = process.env.PORT || 8080;

// ─── Middleware ───────────────────────────────────────────────────────────────
app.use(express.json());
app.use(morgan('combined', { stream: { write: msg => logger.info(msg.trim()) } }));

// ─── Routes ───────────────────────────────────────────────────────────────────
app.use('/checkout', checkoutRoutes);

app.get('/health', (req, res) => {
  res.json({ status: 'UP', service: 'guitarshop-checkout', timestamp: new Date().toISOString() });
});

// ─── Error handler ────────────────────────────────────────────────────────────
app.use((err, req, res, next) => {
  logger.error(err.stack);
  res.status(500).json({ error: 'Internal server error' });
});

app.listen(PORT, () => {
  logger.info(`🎸 GuitarShop Checkout Service running on :${PORT}`);
});
