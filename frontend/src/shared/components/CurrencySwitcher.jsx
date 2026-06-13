import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import useAppStore from '../stores/useAppStore';
import { SUPPORTED_DISPLAY_CURRENCIES } from '../constants/currencies';

const SYMBOL = { TRY: '₺', USD: '$', EUR: '€' };

// Global display-currency toggle (₺/$/€). It writes useAppStore.displayCurrency — the single source every
// money formatter and per-date chart frame reads — so flipping it re-renders the whole app (portfolio,
// charts, beater) in the chosen currency, no extra wiring. ORIGINAL stays a Settings-only option; this
// compact control covers the three concrete currencies.
//
// Expanded sidebar / mobile drawer: a 3-way segmented pill with a sliding accent indicator (mirrors the
// Settings SegmentedControl so it feels native). Collapsed desktop rail: a single symbol button that
// cycles the currencies, since a 3-way control can't fit the narrow rail.
export default function CurrencySwitcher({ collapsed = false, isMobile = false }) {
  const { t } = useTranslation();
  const displayCurrency = useAppStore((s) => s.displayCurrency);
  const setDisplayCurrency = useAppStore((s) => s.setDisplayCurrency);
  const label = t('settings.displayCurrency');

  if (collapsed && !isMobile) {
    const idx = SUPPORTED_DISPLAY_CURRENCIES.indexOf(displayCurrency);
    const next = SUPPORTED_DISPLAY_CURRENCIES[(idx + 1) % SUPPORTED_DISPLAY_CURRENCIES.length] || 'TRY';
    return (
      <button
        type="button"
        onClick={() => setDisplayCurrency(next)}
        title={`${label}: ${displayCurrency}`}
        aria-label={`${label}: ${displayCurrency}`}
        className="w-full flex items-center justify-center py-2 rounded-lg text-fg-muted hover:text-accent hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer font-mono text-[15px] font-bold tabular-nums"
      >
        {SYMBOL[displayCurrency] || '⇄'}
      </button>
    );
  }

  return (
    <div className="px-3 py-1.5">
      <div
        role="group"
        aria-label={label}
        className="flex items-center gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5"
      >
        {SUPPORTED_DISPLAY_CURRENCIES.map((ccy) => {
          const active = displayCurrency === ccy;
          return (
            <button
              key={ccy}
              type="button"
              onClick={() => setDisplayCurrency(ccy)}
              aria-pressed={active}
              aria-label={ccy}
              className="relative flex-1 rounded-md px-2 py-1 text-[11px] font-mono font-semibold transition-colors border-none cursor-pointer bg-transparent"
            >
              {active && (
                <motion.span
                  layoutId="global-currency-pill"
                  className="absolute inset-0 rounded-md bg-accent/15 ring-1 ring-accent/30"
                  transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                />
              )}
              <span className={`relative z-10 inline-flex items-center gap-0.5 ${active ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                <span className="text-[12px]">{SYMBOL[ccy]}</span>
                {ccy}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
