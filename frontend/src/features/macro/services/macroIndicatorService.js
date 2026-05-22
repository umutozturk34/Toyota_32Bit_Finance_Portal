import api from '../../../shared/services/api';

const BASE = '/macro-indicators';

export const macroIndicatorService = {
  list: async ({ category, prominentOnly } = {}) => {
    const res = await api.get(BASE, { params: { category, prominentOnly } });
    return res.data.data;
  },

  history: async (code, { from, to } = {}) => {
    const res = await api.get(`${BASE}/${encodeURIComponent(code)}/history`, {
      params: { from, to },
    });
    return res.data.data;
  },
};
