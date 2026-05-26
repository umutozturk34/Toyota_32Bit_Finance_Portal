'use strict';

const express = require('express');
const cors = require('cors');
const puppeteer = require('puppeteer');

const PORT = Number(process.env.PORT) || 8080;
const IDLE_CLOSE_MS = Number(process.env.PDF_IDLE_CLOSE_MS) || 5 * 60 * 1000;
const DEFAULT_TIMEOUT_MS = Number(process.env.PDF_RENDER_TIMEOUT_MS) || 20000;
const BODY_LIMIT = process.env.PDF_BODY_LIMIT || '1mb';

const LAUNCH_ARGS = [
  '--no-sandbox',
  '--disable-setuid-sandbox',
  '--disable-dev-shm-usage',
  '--disable-gpu',
];

const DEFAULT_PDF_OPTIONS = {
  format: process.env.PDF_PAGE_FORMAT || 'A4',
  printBackground: true,
  margin: {
    top: process.env.PDF_MARGIN_TOP || '14mm',
    right: process.env.PDF_MARGIN_RIGHT || '12mm',
    bottom: process.env.PDF_MARGIN_BOTTOM || '16mm',
    left: process.env.PDF_MARGIN_LEFT || '12mm',
  },
};

let browserPromise = null;
let idleTimer = null;
let inFlightRequests = 0;

function log(level, message, meta) {
  const entry = { ts: new Date().toISOString(), level, message, ...meta };
  process.stdout.write(JSON.stringify(entry) + '\n');
}

async function getBrowser() {
  if (browserPromise) {
    return browserPromise;
  }
  log('info', 'launching puppeteer browser');
  browserPromise = puppeteer
    .launch({
      headless: true,
      args: LAUNCH_ARGS,
      executablePath: process.env.PUPPETEER_EXECUTABLE_PATH || undefined,
    })
    .then((browser) => {
      browser.on('disconnected', () => {
        log('warn', 'puppeteer browser disconnected');
        browserPromise = null;
      });
      return browser;
    })
    .catch((err) => {
      browserPromise = null;
      throw err;
    });
  return browserPromise;
}

function scheduleIdleClose() {
  if (idleTimer) {
    clearTimeout(idleTimer);
  }
  idleTimer = setTimeout(async () => {
    if (inFlightRequests > 0 || !browserPromise) {
      return;
    }
    try {
      const browser = await browserPromise;
      browserPromise = null;
      await browser.close();
      log('info', 'closed idle puppeteer browser');
    } catch (err) {
      log('error', 'failed to close idle browser', { error: String(err) });
    }
  }, IDLE_CLOSE_MS);
  if (idleTimer.unref) {
    idleTimer.unref();
  }
}

async function isBrowserAlive() {
  if (!browserPromise) {
    return false;
  }
  try {
    const browser = await browserPromise;
    return browser.connected !== false && browser.process() !== null;
  } catch {
    return false;
  }
}

function classifyError(err) {
  const msg = String(err && err.message ? err.message : err);
  if (/timeout|timed out/i.test(msg)) {
    return { status: 504, code: 'render_timeout' };
  }
  if (/net::|ERR_|navigation|Navigation/i.test(msg)) {
    return { status: 502, code: 'navigation_failed' };
  }
  if (/Failed to launch|spawn|ENOENT|executablePath/i.test(msg)) {
    return { status: 503, code: 'browser_unavailable' };
  }
  return { status: 500, code: 'render_failed' };
}

const app = express();
app.use(cors());
app.use(express.json({ limit: BODY_LIMIT }));

app.use((req, res, next) => {
  const start = Date.now();
  res.on('finish', () => {
    log('info', 'request', {
      method: req.method,
      path: req.path,
      status: res.statusCode,
      durationMs: Date.now() - start,
      target: req.body && req.body.url ? req.body.url : undefined,
    });
  });
  next();
});

app.get('/health', async (_req, res) => {
  const alive = await isBrowserAlive();
  res.json({ status: 'ok', browserAlive: alive });
});

app.post('/render', async (req, res) => {
  const {
    url,
    htmlContent,
    waitFor,
    timeout = DEFAULT_TIMEOUT_MS,
    pdfOptions = {},
    extraHeaders,
  } = req.body || {};

  if ((!url || typeof url !== 'string') && (!htmlContent || typeof htmlContent !== 'string')) {
    return res.status(400).json({ error: 'invalid_request', message: 'url or htmlContent is required' });
  }

  inFlightRequests += 1;
  if (idleTimer) {
    clearTimeout(idleTimer);
    idleTimer = null;
  }

  let page;
  try {
    const browser = await getBrowser();
    page = await browser.newPage();
    if (extraHeaders && typeof extraHeaders === 'object') {
      await page.setExtraHTTPHeaders(extraHeaders);
    }
    if (htmlContent) {
      await page.setContent(htmlContent, { waitUntil: 'networkidle0', timeout });
    } else {
      await page.goto(url, { waitUntil: 'networkidle0', timeout });
    }

    if (waitFor) {
      const expr = typeof waitFor === 'string' ? waitFor : String(waitFor);
      await page.waitForFunction(expr, { timeout });
    }

    const mergedPdfOptions = {
      ...DEFAULT_PDF_OPTIONS,
      ...pdfOptions,
      margin: { ...DEFAULT_PDF_OPTIONS.margin, ...(pdfOptions.margin || {}) },
    };
    const pdfBuffer = await page.pdf(mergedPdfOptions);

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Length', pdfBuffer.length);
    res.status(200).end(pdfBuffer);
  } catch (err) {
    const { status, code } = classifyError(err);
    log('error', 'render failed', { code, error: String(err && err.message ? err.message : err) });
    if (!res.headersSent) {
      res.status(status).json({ error: code, message: String(err && err.message ? err.message : err) });
    }
  } finally {
    if (page) {
      try {
        await page.close();
      } catch (closeErr) {
        log('warn', 'failed to close page', { error: String(closeErr) });
      }
    }
    inFlightRequests -= 1;
    if (inFlightRequests <= 0) {
      scheduleIdleClose();
    }
  }
});

const server = app.listen(PORT, () => {
  log('info', 'pdf-service listening', { port: PORT });
});

async function shutdown(signal) {
  log('info', 'shutdown signal received', { signal });
  server.close(() => log('info', 'http server closed'));
  if (browserPromise) {
    try {
      const browser = await browserPromise;
      await browser.close();
    } catch (err) {
      log('warn', 'browser close failed on shutdown', { error: String(err) });
    }
  }
  process.exit(0);
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
