export function calculateSMA(data, period) {
  if (!data || data.length < period || period < 1) return [];
  const result = [];
  let sum = 0;
  for (let i = 0; i < period; i++) {
    sum += data[i].close;
  }
  result.push({ time: data[period - 1].time, value: sum / period });
  for (let i = period; i < data.length; i++) {
    sum += data[i].close - data[i - period].close;
    result.push({ time: data[i].time, value: sum / period });
  }
  return result;
}
export function calculateEMA(data, period) {
  if (!data || data.length < period || period < 1) return [];
  const multiplier = 2 / (period + 1);
  const result = [];
  let sum = 0;
  for (let i = 0; i < period; i++) {
    sum += data[i].close;
  }
  let ema = sum / period;
  result.push({ time: data[period - 1].time, value: ema });
  for (let i = period; i < data.length; i++) {
    ema = (data[i].close - ema) * multiplier + ema;
    result.push({ time: data[i].time, value: ema });
  }
  return result;
}
export function calculateRSI(data, period = 14) {
  if (!data || data.length < period + 1 || period < 1) return [];
  const result = [];
  const changes = [];
  for (let i = 1; i < data.length; i++) {
    changes.push(data[i].close - data[i - 1].close);
  }
  let avgGain = 0;
  let avgLoss = 0;
  for (let i = 0; i < period; i++) {
    if (changes[i] >= 0) avgGain += changes[i];
    else avgLoss += Math.abs(changes[i]);
  }
  avgGain /= period;
  avgLoss /= period;
  const rs = avgLoss === 0 ? 100 : avgGain / avgLoss;
  const rsi = avgLoss === 0 ? 100 : 100 - 100 / (1 + rs);
  result.push({ time: data[period].time, value: rsi });
  for (let i = period; i < changes.length; i++) {
    const gain = changes[i] >= 0 ? changes[i] : 0;
    const loss = changes[i] < 0 ? Math.abs(changes[i]) : 0;
    avgGain = (avgGain * (period - 1) + gain) / period;
    avgLoss = (avgLoss * (period - 1) + loss) / period;
    const smoothedRS = avgLoss === 0 ? 100 : avgGain / avgLoss;
    const smoothedRSI = avgLoss === 0 ? 100 : 100 - 100 / (1 + smoothedRS);
    result.push({ time: data[i + 1].time, value: smoothedRSI });
  }
  return result;
}

export function calculateMACD(data, fastPeriod = 12, slowPeriod = 26, signalPeriod = 9) {
  if (!data || data.length < slowPeriod + signalPeriod) return { macd: [], signal: [], histogram: [] };

  const fastMult = 2 / (fastPeriod + 1);
  const slowMult = 2 / (slowPeriod + 1);
  const sigMult = 2 / (signalPeriod + 1);

  let fastEma = 0;
  let slowEma = 0;
  for (let i = 0; i < fastPeriod; i++) fastEma += data[i].close;
  fastEma /= fastPeriod;
  for (let i = 0; i < slowPeriod; i++) slowEma += data[i].close;
  slowEma /= slowPeriod;

  const macdValues = [];
  for (let i = 0; i < data.length; i++) {
    if (i < fastPeriod) {
      fastEma = (i === fastPeriod - 1) ? fastEma : fastEma;
    } else {
      fastEma = (data[i].close - fastEma) * fastMult + fastEma;
    }
    if (i < slowPeriod) {
      slowEma = (i === slowPeriod - 1) ? slowEma : slowEma;
    } else {
      slowEma = (data[i].close - slowEma) * slowMult + slowEma;
    }
    if (i >= slowPeriod - 1) {
      macdValues.push({ time: data[i].time, value: fastEma - slowEma });
    }
  }

  const macdResult = [];
  const signalResult = [];
  const histogramResult = [];

  let signalEma = 0;
  for (let i = 0; i < signalPeriod && i < macdValues.length; i++) {
    signalEma += macdValues[i].value;
  }
  signalEma /= signalPeriod;

  for (let i = 0; i < macdValues.length; i++) {
    const mv = macdValues[i];
    macdResult.push({ time: mv.time, value: mv.value });

    if (i < signalPeriod - 1) continue;

    if (i === signalPeriod - 1) {
      signalResult.push({ time: mv.time, value: signalEma });
      histogramResult.push({ time: mv.time, value: mv.value - signalEma, color: mv.value - signalEma >= 0 ? 'rgba(38,166,154,0.7)' : 'rgba(239,83,80,0.7)' });
    } else {
      signalEma = (mv.value - signalEma) * sigMult + signalEma;
      signalResult.push({ time: mv.time, value: signalEma });
      histogramResult.push({ time: mv.time, value: mv.value - signalEma, color: mv.value - signalEma >= 0 ? 'rgba(38,166,154,0.7)' : 'rgba(239,83,80,0.7)' });
    }
  }

  return { macd: macdResult, signal: signalResult, histogram: histogramResult };
}
