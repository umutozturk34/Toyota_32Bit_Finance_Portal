import { commodityVisual } from '../../../shared/icons/commodities';
import { flagEmoji } from '../flagEmoji';

// A single compact chip in the horizontal currency/gold strip: flag + ISO code for currencies, gold glyph +
// localized name for gold (the gold "codes" aren't user-facing). The full currency name lives in the title and
// in the detail header below. shrink-0 keeps chips from squishing so the strip scrolls horizontally instead.
export default function RateChip({ active, code, label, onClick, kind }) {
  const isGold = kind === 'GOLD';
  const flag = isGold ? null : flagEmoji(code);
  const goldIcon = isGold ? commodityVisual(code) : null;
  return (
    <button
      onClick={onClick}
      title={label}
      data-active={active ? 'true' : undefined}
      className={`shrink-0 inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold border cursor-pointer transition-all ${
        active
          ? 'bg-accent/15 border-accent/40 text-accent'
          : 'bg-transparent border-border-default text-fg-muted hover:text-fg hover:border-border-hover'
      }`}
    >
      {flag && <span className="shrink-0 text-base leading-none">{flag}</span>}
      {goldIcon && <goldIcon.Icon className={`shrink-0 h-4 w-4 ${goldIcon.color}`} />}
      <span className="whitespace-nowrap">{isGold ? label : code}</span>
    </button>
  );
}
