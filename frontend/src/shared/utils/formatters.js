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
const visibleDecimals = (value, base, cap = 8) => {
    const n = Math.abs(Number(value));
    if (!Number.isFinite(n) || n === 0) return base;
    let d = base;
    while (d < cap && n < 0.5 * 10 ** -d) d++;
    return d;
};

// Decimal places that keep a price legible across magnitudes without collapsing toward zero. Shared by the
// chart axis, the crosshair legend, and the metadata/money formatting so a single value never renders three
// different ways: sub-0.001 (a sub-cent fund NAV in USD, cheap crypto) gets 6 places, sub-0.1 gets 4, and an
// ordinary price keeps 2.
export const priceDecimals = (value) => {
    const n = Math.abs(Number(value));
    if (!Number.isFinite(n) || n === 0) return 2;
    if (n < 0.001) return 6;
    if (n < 0.1) return 4;
    return 2;
};

export const formatPrice = (
    price,
    { currency, locale, minDecimals = 2, maxDecimals = 2 } = {},
) => {
    if (price === null || price === undefined) return 'N/A';
    const resolvedLocale = locale || currentLocaleTag();
    const effectiveMax = Math.max(maxDecimals, visibleDecimals(price, maxDecimals));
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

export const formatPriceTRY = (price) => {
    if (price === null || price === undefined) return 'N/A';
    const num = Number(price);
    const decimals = num < 10 ? 4 : num < 1000 ? 3 : 2;
    return formatPrice(num, { currency: 'TRY', minDecimals: 2, maxDecimals: decimals });
};

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
    return date.toLocaleString(locale || currentLocaleTag(), {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Europe/Istanbul',
    });
};
