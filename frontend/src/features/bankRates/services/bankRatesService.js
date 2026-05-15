import api from '../../../shared/services/api';

const BASE = '/bank-rates';

export const bankRatesService = {
  list: async ({ currency, kind = 'CURRENCY' } = {}) => {
    const res = await api.get(BASE, { params: { currency, kind } });
    return res.data.data;
  },
  listCurrencies: async ({ kind = 'CURRENCY' } = {}) => {
    const res = await api.get(`${BASE}/currencies`, { params: { kind } });
    return res.data.data;
  },
};
