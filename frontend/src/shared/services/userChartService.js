import api from './api';

const PREF_PATH = (type, code) => `/user/chart-preferences/${type}/${encodeURIComponent(code)}`;
const DRAW_PATH = (type, code) => `/user/chart-drawings/${type}/${encodeURIComponent(code)}`;

export const userChartPreferenceService = {
  get: async (type, code) => {
    const response = await api.get(PREF_PATH(type, code));
    return response.data.data;
  },

  save: async ({ type, code, config }) => {
    const response = await api.put(PREF_PATH(type, code), { config });
    return response.data.data;
  },
};

export const userChartDrawingService = {
  get: async (type, code) => {
    const response = await api.get(DRAW_PATH(type, code));
    return response.data.data;
  },

  save: async ({ type, code, drawings }) => {
    const response = await api.put(DRAW_PATH(type, code), { drawings });
    return response.data.data;
  },
};
