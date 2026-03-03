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
    { currency, locale = 'tr-TR', minDecimals = 2, maxDecimals = 2 } = {},
) => {
    if (price === null || price === undefined) return 'N/A';
    const opts = {
        minimumFractionDigits: minDecimals,
        maximumFractionDigits: maxDecimals,
    };
    if (currency) {
        opts.style = 'currency';
        opts.currency = currency;
    }
    return new Intl.NumberFormat(locale, opts).format(price);
};

export const formatPriceUSD = (price, maxDecimals = 2) =>
    formatPrice(price, { currency: 'USD', locale: 'en-US', maxDecimals });

export const formatPriceTRY = (price) =>
    formatPrice(price, { currency: 'TRY', locale: 'tr-TR' });

export const formatCompactNumber = (number, currency = 'USD') => {
    if (number === null || number === undefined) return 'N/A';
    return new Intl.NumberFormat('en-US', {
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

export const formatChange = (change, decimals = 4, locale = 'tr-TR') => {
    if (change === null || change === undefined) return 'N/A';
    const prefix = change > 0 ? '+' : '';
    return (
        prefix +
        new Intl.NumberFormat(locale, {
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

export const formatDateLong = (dateString, locale = 'tr-TR') => {
    const date = new Date(dateString);
    return date.toLocaleDateString(locale, {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
    });
};
