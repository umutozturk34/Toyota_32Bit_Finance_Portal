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

/** True for the three size/benchmark indices (BIST 30/50/100) — a stock's "blue-chip" stature signal. */
export function isSizeIndex(code) {
  return Boolean(SIZE_INDEX_NAMES[bareIndexCode(code)]);
}

/** Friendly name for a BIST index code; falls back to the bare code for anything not in the catalog. */
export function indexFriendlyName(code) {
  const bare = bareIndexCode(code);
  return SIZE_INDEX_NAMES[bare] || SECTOR_INDEX_NAMES[bare] || bare;
}
