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

export const formatPrice = (
    price,
    { currency, locale, minDecimals = 2, maxDecimals = 2 } = {},
) => {
    if (price === null || price === undefined) return 'N/A';
    const resolvedLocale = locale || currentLocaleTag();
    const opts = {
        minimumFractionDigits: minDecimals,
        maximumFractionDigits: maxDecimals,
    };
    if (currency) {
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

export const formatCompactTRY = (price) => {
    if (price === null || price === undefined) return 'N/A';
    if (price >= 1_000_000) return `${(price / 1_000_000).toFixed(1)}M ₺`;
    if (price >= 100_000) return `${(price / 1_000).toFixed(0)}K ₺`;
    return formatPriceTRY(price);
};

export const formatPriceCompactTRY = (price) => {
    if (price === null || price === undefined) return 'N/A';
    const num = Number(price);
    if (num >= 1_000_000_000) return `${(num / 1_000_000_000).toFixed(2)}B ₺`;
    if (num >= 1_000_000) return `${(num / 1_000_000).toFixed(2)}M ₺`;
    if (num >= 10_000) return `${(num / 1_000).toFixed(1)}K ₺`;
    return formatPriceTRY(num);
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
    const prefix = percent > 0 ? '+' : '';
    return `${prefix}${percent.toFixed(2)}%`;
};

export const formatPercentAbs = (percent, decimals = 2) => {
    if (percent === null || percent === undefined) return '0.00%';
    return `${Math.abs(percent).toFixed(decimals)}%`;
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
