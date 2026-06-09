// Random id that works in every browser and context. crypto.randomUUID() is restricted to secure contexts
// (HTTPS) and Safari 15.4+, so calling it bare throws on older Safari or a non-HTTPS origin and takes the
// page down. Fall back to getRandomValues (Safari 11+, any context) and finally Math.random so id generation
// can never crash a render.
export function randomId() {
  const c = typeof crypto !== 'undefined' ? crypto : undefined;
  if (c && typeof c.randomUUID === 'function') return c.randomUUID();
  if (c && typeof c.getRandomValues === 'function') {
    const b = c.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    const h = Array.from(b, (x) => x.toString(16).padStart(2, '0'));
    return `${h.slice(0, 4).join('')}-${h.slice(4, 6).join('')}-${h.slice(6, 8).join('')}-${h.slice(8, 10).join('')}-${h.slice(10).join('')}`;
  }
  return `${Date.now().toString(16)}${Math.random().toString(16).slice(2, 10)}`;
}
