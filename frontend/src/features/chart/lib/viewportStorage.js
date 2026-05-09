const PREFIX = 'chart-viewport';
const INDEX_KEY = 'chart-viewport-index';
const MAX_ENTRIES = 60;

const keyOf = (assetType, code) => `${PREFIX}:${assetType}:${code}`;

function readIndex() {
  try {
    return JSON.parse(localStorage.getItem(INDEX_KEY) || '[]');
  } catch {
    return [];
  }
}

function writeIndex(list) {
  try {
    localStorage.setItem(INDEX_KEY, JSON.stringify(list));
  } catch {
    /* quota exceeded — ignore, viewport persistence is best-effort */
  }
}

export function readViewport(assetType, code) {
  if (!assetType || !code) return null;
  try {
    const raw = localStorage.getItem(keyOf(assetType, code));
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed.from === 'number' && typeof parsed.to === 'number') {
      return parsed;
    }
    return null;
  } catch {
    return null;
  }
}

export function writeViewport(assetType, code, range) {
  if (!assetType || !code || !range) return;
  const k = keyOf(assetType, code);
  try {
    localStorage.setItem(k, JSON.stringify({ from: range.from, to: range.to }));
  } catch {
    return;
  }
  const idx = readIndex().filter((entry) => entry !== k);
  idx.push(k);
  while (idx.length > MAX_ENTRIES) {
    const oldest = idx.shift();
    try { localStorage.removeItem(oldest); } catch { /* ignore */ }
  }
  writeIndex(idx);
}
