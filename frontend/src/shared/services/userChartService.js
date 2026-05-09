import api from './api';

function backendType(type) {
  return type === 'BIST' ? 'STOCK' : type;
}

function backendCode(type, code) {
  if (type === 'BIST' && code && !code.endsWith('.IS')) return `${code}.IS`;
  return code;
}

const BUNDLE_PATH = (type, code) => `/user/chart-data/${backendType(type)}/${encodeURIComponent(backendCode(type, code))}`;

export const userChartDataService = {
  get: async (type, code, range) => {
    const params = range ? { range } : {};
    const response = await api.get(BUNDLE_PATH(type, code), { params });
    return response.data.data;
  },
};

export const userChartPreferenceService = {
  save: async ({ type, code, config }) => {
    const response = await api.put(`${BUNDLE_PATH(type, code)}/preferences`, { config });
    return response.data.data;
  },
};

export const userChartDrawingService = {
  save: async ({ type, code, drawings }) => {
    const response = await api.put(`${BUNDLE_PATH(type, code)}/drawings`, { drawings });
    return response.data.data;
  },
};
