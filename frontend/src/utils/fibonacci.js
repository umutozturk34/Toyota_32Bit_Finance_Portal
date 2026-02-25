export const FIB_LEVELS = [0, 0.236, 0.382, 0.5, 0.618, 0.786, 1, 1.618];
export const FIB_LABELS = {
    0: '0%',
    0.236: '23.6%',
    0.382: '38.2%',
    0.5: '50%',
    0.618: '61.8%',
    0.786: '78.6%',
    1: '100%',
    1.618: '161.8%',
};
export function calculateFibRetracement(startPrice, endPrice) {
    const range = endPrice - startPrice;
    return FIB_LEVELS.map(level => ({
        level,
        price: startPrice + range * level,
        label: FIB_LABELS[level],
    }));
}
export function calculateFibExtension(startPrice, endPrice, pivotPrice) {
    const range = Math.abs(endPrice - startPrice);
    const direction = pivotPrice < endPrice ? 1 : -1;
    return FIB_LEVELS.map(level => ({
        level,
        price: pivotPrice + range * level * direction,
        label: FIB_LABELS[level],
    }));
}
