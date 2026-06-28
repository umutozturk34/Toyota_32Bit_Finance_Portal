import i18n from '../i18n/config';

export const currentLocaleTag = () => {
    const lang = i18n.language || i18n.options.fallbackLng || 'en';
    return lang === 'tr' ? 'tr-TR' : 'en-US';
};

export const getChangeClass = (change) => {
    if (change > 0) return 'positive';
    if (change < 0) return 'negative';
    return 'neutral';
};

export const changeColors = {
    positive: 'text-success',
    negative: 'text-danger',
    neutral: 'text-fg-muted',
};

export const changeBg = {
    positive: 'bg-success/10',
    negative: 'bg-danger/10',
    neutral: 'bg-fg-muted/10',
};

// Fewest decimals (capped) that keep a small but non-zero magnitude from rounding to a flat zero, so a
// genuine 0.0001 move is never shown as "0.00". Magnitudes already visible at `base` are returned unchanged.
export const visibleDecimals = (value, base, cap = 8) => {
    const n = Math.abs(Number(value));
    if (!Number.isFinite(n) || n === 0) return base;
    let d = base;
    while (d < cap && n < 0.5 * 10 ** -d) d++;
    // Safety net for binary-FP boundary cases the threshold loop stops one digit early on (e.g. an exact-half
    // 5e-7): widen until the value actually renders a non-zero digit, or the cap is hit, so a real holding
    // never shows as a flat "0.000000".
    while (d < cap && /^0\.?0*$/.test(n.toFixed(d))) d++;
    // If we widened for a tiny value AND rounding at d rounds UP above the true figure (e.g. 0.000046 →
    // "0.00005"), show one more decimal so it reads accurately (0.000046), not a misleading single digit.
    if (d > base) {
        while (d < cap && Number(n.toFixed(d)) > n) d++;
    }
    return d;
};

// Decimal places that keep a price legible across magnitudes without collapsing toward zero. The SINGLE
// source of truth for price precision — shared by the chart axis, the crosshair legend, the headline cards
// and the metadata/money formatting so a value never renders different ways across surfaces. A sub-1 price
// (fund NAV like 0,4631, cheap crypto/forex) keeps 4 significant decimals (≥1 ticks in kuruş, so 2 is exact),
// and a sub-0.001 value gets 6. Trailing zeros are trimmed by the minDecimals floor at the format step.
export const priceDecimals = (value, { forex = false } = {}) => {
    const n = Math.abs(Number(value));
    if (!Number.isFinite(n) || n === 0) return 2;
    if (n < 0.001) return 6;
    // A forex rate carries sub-kuruş precision (e.g. 32,5432) even at magnitude >=1, where the generic
    // ladder collapses it to 2 — so a forex surface keeps 4, matching the precise list/card display.
    if (forex) return 4;
    if (n < 1) return 4;
    return 2;
};

export const formatPrice = (
    price,
    { currency, locale, minDecimals = 2, maxDecimals } = {},
) => {
    if (price === null || price === undefined) return 'N/A';
    const resolvedLocale = locale || currentLocaleTag();
    // Default the cap to the shared magnitude-aware ladder (so a bare formatPrice(0.4631) shows 0,4631, not
    // a collapsed 0,46); an explicit maxDecimals still wins for callers that need a fixed width.
    const cap = maxDecimals ?? priceDecimals(price);
    const effectiveMax = Math.max(cap, visibleDecimals(price, cap));
    const opts = {
        minimumFractionDigits: Math.min(minDecimals, effectiveMax),
        maximumFractionDigits: effectiveMax,
    };
    if (currency && /^[A-Z]{3}$/.test(String(currency))) {
        opts.style = 'currency';
        opts.currency = currency;
    }
    return new Intl.NumberFormat(resolvedLocale, opts).format(price);
};

export const formatPriceUSD = (price, maxDecimals = 2) =>
    formatPrice(price, { currency: 'USD', locale: 'en-US', maxDecimals });

export const formatPriceTRY = (price) =>
    formatPrice(price, { currency: 'TRY' });

export const formatCompactNumber = (number, currency = 'USD') => {
    if (number === null || number === undefined) return 'N/A';
    return new Intl.NumberFormat(currentLocaleTag(), {
        notation: 'compact',
        compactDisplay: 'short',
        style: 'currency',
        currency,
        minimumFractionDigits: 1,
        maximumFractionDigits: 2,
    }).format(number);
};

// Chooses the fullest money string that fits `maxChars` without ever CSS-clipping digits. The full,
// grouped value is preferred (so an ordinary 6-9 figure total stays exact); only a genuinely-too-wide
// value steps down — first by dropping decimals, then to compact notation (₺365,54 Tn) — so it fits the
// card instead of spilling outside it. `maxChars` of 0/unset means "no width limit" → always full.
export const fitMoney = (value, { currency, locale, maxChars = 0, maxDecimals = 2 } = {}) => {
    if (value === null || value === undefined) return 'N/A';
    const num = Number(value);
    if (!Number.isFinite(num)) return 'N/A';
    const resolvedLocale = locale || currentLocaleTag();
    const full = formatPrice(num, { currency, locale: resolvedLocale, minDecimals: 2, maxDecimals });
    if (!maxChars || full.length <= maxChars) return full;
    const full0 = formatPrice(num, { currency, locale: resolvedLocale, minDecimals: 0, maxDecimals: 0 });
    if (full0.length <= maxChars) return full0;
    const compactOpts = { notation: 'compact', compactDisplay: 'short', maximumFractionDigits: 2 };
    if (currency && /^[A-Z]{3}$/.test(String(currency))) {
        compactOpts.style = 'currency';
        compactOpts.currency = currency;
    }
    const compact2 = new Intl.NumberFormat(resolvedLocale, compactOpts).format(num);
    if (compact2.length <= maxChars) return compact2;
    return new Intl.NumberFormat(resolvedLocale, { ...compactOpts, maximumFractionDigits: 0 }).format(num);
};

export const formatVolume = (volume) => {
    if (!volume) return 'N/A';
    if (volume >= 1_000_000_000_000) return `${(volume / 1_000_000_000_000).toFixed(1)}T`;
    if (volume >= 1_000_000_000) return `${(volume / 1_000_000_000).toFixed(1)}B`;
    if (volume >= 1_000_000) return `${(volume / 1_000_000).toFixed(1)}M`;
    if (volume >= 1_000) return `${(volume / 1_000).toFixed(1)}K`;
    return String(volume);
};

export const formatChange = (change, decimals = 4, locale) => {
    if (change === null || change === undefined) return 'N/A';
    const prefix = change > 0 ? '+' : '';
    return (
        prefix +
        new Intl.NumberFormat(locale || currentLocaleTag(), {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals,
        }).format(change)
    );
};

export const formatPercent = (percent) => {
    if (percent === null || percent === undefined) return 'N/A';
    const n = Number(percent);
    const prefix = n > 0 ? '+' : '';
    return `${prefix}${n.toFixed(visibleDecimals(n, 2))}%`;
};

// Locale-aware percent with magnitude-adaptive precision. Multi-year BIST/crypto returns reach the tens of
// thousands of percent (a 5Y winner can be +39295.68%); at a fixed 2 decimals that prints as an unreadable
// "+39295.68%". So large figures drop decimals and gain locale thousands separators ("+39.296%" tr /
// "+39,296%" en), while ordinary small moves keep 2 decimals. Use for return/excess figures, not raw prices.
export const formatPercentSmart = (percent) => {
    if (percent === null || percent === undefined) return 'N/A';
    const n = Number(percent);
    if (!Number.isFinite(n)) return 'N/A';
    const magnitude = Math.abs(n);
    const digits = magnitude >= 1000 ? 0 : magnitude >= 100 ? 1 : visibleDecimals(n, 2);
    const prefix = n > 0 ? '+' : '';
    return `${prefix}${n.toLocaleString(currentLocaleTag(), {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
    })}%`;
};

export const formatPercentAbs = (percent, decimals = 2) => {
    if (percent === null || percent === undefined) return '0.00%';
    return `${Math.abs(percent).toFixed(visibleDecimals(percent, decimals))}%`;
};

export const formatDateTimeShort = (dateString, locale) => {
    const date = new Date(dateString);
    if (!dateString || Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString(locale || currentLocaleTag(), {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Europe/Istanbul',
    });
};

export const formatDateTimeFull = (dateString, locale) => {
    const date = new Date(dateString);
    if (!dateString || Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString(locale || currentLocaleTag(), {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Europe/Istanbul',
    });
};
