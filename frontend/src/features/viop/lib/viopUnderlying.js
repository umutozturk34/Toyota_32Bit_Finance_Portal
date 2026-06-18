import { assetRoute } from '../../watch/lib/watchConstants';

// VIOP categories whose underlying is a BIST-listed instrument reachable at /stocks/<code>.IS: equities (PAY) and
// indices, which live in the stock catalog under their `.IS` code. Currency/metal underlyings map to
// forex/commodity assets whose code form is NOT a clean 1:1 with the VIOP underlying, so those are left
// un-linked rather than risk a dead link.
const STOCK_ROUTED_CATEGORIES = new Set(['PAY_FUTURE', 'PAY_OPTION', 'INDEX_FUTURE', 'INDEX_OPTION']);
const INDEX_CATEGORIES = new Set(['INDEX_FUTURE', 'INDEX_OPTION']);

/**
 * Resolves a VIOP contract's underlying to its tradable-asset detail route, or null when it can't be safely linked.
 * Strips the İş Yatırım encoding: the {@code D_} prefix, a dotted suffix (e.g. {@code AKBNK.E}), and — for indices —
 * the trailing {@code D} (e.g. {@code D_XU030D → XU030}). Returns {@code { code, route }} for the cleaned code.
 */
export function viopUnderlyingRoute(meta) {
  const raw = meta?.underlying;
  const category = meta?.category;
  if (!raw || !STOCK_ROUTED_CATEGORIES.has(category)) return null;

  let code = String(raw).trim().toUpperCase();
  if (code.startsWith('D_')) code = code.slice(2);
  const dot = code.indexOf('.');
  if (dot > 0) code = code.slice(0, dot);
  if (INDEX_CATEGORIES.has(category) && code.endsWith('D')) code = code.slice(0, -1);

  if (!code) return null;
  // BIST equities and indices live in the stock catalog under their `.IS` code (e.g. ALARK -> ALARK.IS,
  // XU030 -> XU030.IS), which is what the /stocks/<code> route and its detail endpoint resolve by. The VIOP
  // underlying carries only the bare ticker, so append the suffix; the displayed `code` stays bare for clarity.
  const bistCode = code.endsWith('.IS') ? code : `${code}.IS`;
  return { code, route: assetRoute('STOCK', bistCode) };
}
