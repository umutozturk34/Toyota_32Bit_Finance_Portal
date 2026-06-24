import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import useAppStore from '../stores/useAppStore';
import { SUPPORTED_DISPLAY_CURRENCIES } from '../constants/currencies';

// The control also exposes ORIGINAL (every asset in its own native currency) so the user can flip back to it
// straight from the sidebar instead of digging into Settings — the store defaults to ORIGINAL on first load,
// and once a concrete currency is picked there was previously no sidebar path back.
const SWITCHER_CURRENCIES = [...SUPPORTED_DISPLAY_CURRENCIES, 'ORIGINAL'];
const SYMBOL = { TRY: '₺', USD: '$', EUR: '€', ORIGINAL: '⇄' };

// Global display-currency toggle (₺/$/€/⇄). It writes useAppStore.displayCurrency — the single source every
// money formatter and per-date chart frame reads — so flipping it re-renders the whole app (portfolio,
// charts, beater) in the chosen currency (or each asset's native, for ORIGINAL), no extra wiring.
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
    const idx = SWITCHER_CURRENCIES.indexOf(displayCurrency);
    const next = SWITCHER_CURRENCIES[(idx + 1) % SWITCHER_CURRENCIES.length] || 'TRY';
    return (
      <button
        type="button"
        onClick={() => setDisplayCurrency(next)}
        title={`${label}: ${displayCurrency}`}
        aria-label={`${label}: ${displayCurrency}`}
        className="w-full flex items-center overflow-hidden px-0 py-2 rounded-lg text-fg-muted hover:text-accent hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer font-mono text-[15px] font-bold tabular-nums"
      >
        <span className="flex items-center justify-center w-12 shrink-0">
          {SYMBOL[displayCurrency] || '⇄'}
        </span>
      </button>
    );
  }

  return (
    <div className="px-2 py-1">
      <div
        role="group"
        aria-label={label}
        className="flex items-stretch gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5"
      >
        {SWITCHER_CURRENCIES.map((ccy) => {
          const active = displayCurrency === ccy;
          const text = ccy === 'ORIGINAL'
            ? t('settings.currencyOriginalShort', { defaultValue: 'Orj' })
            : ccy;
          return (
            <button
              key={ccy}
              type="button"
              onClick={() => setDisplayCurrency(ccy)}
              aria-pressed={active}
              aria-label={ccy}
              title={ccy === 'ORIGINAL' ? label : ccy}
              className="relative flex-1 min-w-0 rounded-md px-1 py-1.5 text-[10.5px] font-mono font-semibold transition-colors border-none cursor-pointer bg-transparent"
            >
              {active && (
                <motion.span
                  layoutId="global-currency-pill"
                  className="absolute inset-0 rounded-md bg-accent/15 ring-1 ring-accent/30"
                  transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                />
              )}
              <span className={`relative z-10 flex items-center justify-center gap-0.5 leading-none ${active ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                <span className="text-[12px] leading-none">{SYMBOL[ccy]}</span>
                <span className="leading-none">{text}</span>
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
