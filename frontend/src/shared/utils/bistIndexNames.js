// BIST index code → friendly name, keyless (the tracked_asset rows carry no display_name for indices).
// Used wherever an index code is shown to the user so XU100 reads as "BIST 100" instead of a bare ticker.
// The three SIZE indices (BIST 30/50/100) signal a stock's stature, so they are tiered apart from the
// sector indices in the UI.
const SIZE_INDEX_NAMES = {
  XU030: 'BIST 30',
  XU050: 'BIST 50',
  XU100: 'BIST 100',
};

const SECTOR_INDEX_NAMES = {
  XUMAL: 'BIST Mali',
  XUHIZ: 'BIST Hizmetler',
  XUSIN: 'BIST Sınai',
  XUTEK: 'BIST Teknoloji',
  XBANK: 'BIST Banka',
  XGMYO: 'BIST GYO',
  XKMYA: 'BIST Kimya, Petrol, Plastik',
  XMESY: 'BIST Metal Eşya, Makine',
  XMADN: 'BIST Madencilik',
  XTEKS: 'BIST Tekstil, Deri',
  XELKT: 'BIST Elektrik',
  XILTM: 'BIST İletişim',
  XINSA: 'BIST İnşaat',
  XFINK: 'BIST Finansal Kiralama, Faktoring',
  XSGRT: 'BIST Sigorta',
  XSPOR: 'BIST Spor',
  XTCRT: 'BIST Ticaret',
  XBLSM: 'BIST Bilişim',
  XKAGT: 'BIST Orman, Kağıt, Basım',
  XTAST: 'BIST Taş, Toprak',
  XKURY: 'BIST Kurumsal Yönetim',
  XKOBI: 'BIST KOBİ Sanayi',
  XHARZ: 'BIST Halka Arz',
  XBANA: 'BIST Banka Dışı Mali',
};

function bareIndexCode(code) {
  return (code || '').replace('.IS', '').toUpperCase();
}

// Canonical display order for the size/benchmark indices: 30 → 50 → 100 (ascending breadth), which is
// how investors expect to scan them — not the arbitrary order the backend returns rows in.
const SIZE_INDEX_ORDER = ['XU030', 'XU050', 'XU100'];

/**
 * Orders index assets for display: the size indices first in 30 → 50 → 100 order, then any other indices
 * in their original (weight/data) order. Pure — returns a new array; sort is stable so non-size order is kept.
 */
export function mainIndexSort(indices) {
  const rankOf = (idx) => {
    const i = SIZE_INDEX_ORDER.indexOf(bareIndexCode(idx?.code));
    return i === -1 ? Number.MAX_SAFE_INTEGER : i;
  };
  return [...(indices || [])].sort((a, b) => rankOf(a) - rankOf(b));
}

/** True for the three size/benchmark indices (BIST 30/50/100) — a stock's "blue-chip" stature signal. */
export function isSizeIndex(code) {
  return Boolean(SIZE_INDEX_NAMES[bareIndexCode(code)]);
}

/** Friendly name for a BIST index code; falls back to the bare code for anything not in the catalog. */
export function indexFriendlyName(code) {
  const bare = bareIndexCode(code);
  return SIZE_INDEX_NAMES[bare] || SECTOR_INDEX_NAMES[bare] || bare;
}

/** True when the code is a known BIST index (size or sector) rather than an ordinary stock. */
export function isIndexCode(code) {
  const bare = bareIndexCode(code);
  return Boolean(SIZE_INDEX_NAMES[bare] || SECTOR_INDEX_NAMES[bare]);
}
