import api from '../../../shared/services/api';

const depositsBase = (portfolioId) => `/portfolios/${portfolioId}/deposits`;
const bondsBase = (portfolioId) => `/portfolios/${portfolioId}/bonds`;
const fixedIncomeBase = (portfolioId) => `/portfolios/${portfolioId}/fixed-income`;

export const fixedIncomeService = {
  summary: async (portfolioId) => {
    const res = await api.get(`${fixedIncomeBase(portfolioId)}/summary`);
    return res.data.data;
  },

  history: async (portfolioId, period) => {
    const res = await api.get(`${fixedIncomeBase(portfolioId)}/history`, { params: { period } });
    return res.data.data;
  },

  deposits: {
    list: async (portfolioId) => {
      const res = await api.get(depositsBase(portfolioId));
      return res.data.data;
    },

    add: async (portfolioId, payload) => {
      const res = await api.post(depositsBase(portfolioId), payload);
      return res.data.data;
    },

    update: async (portfolioId, depositId, payload) => {
      const res = await api.put(`${depositsBase(portfolioId)}/${depositId}`, payload);
      return res.data.data;
    },

    close: async (portfolioId, depositId, payload) => {
      const res = await api.post(`${depositsBase(portfolioId)}/${depositId}/close`, null, { params: payload });
      return res.data.data;
    },

    reopen: async (portfolioId, depositId) => {
      const res = await api.post(`${depositsBase(portfolioId)}/${depositId}/reopen`);
      return res.data.data;
    },

    delete: async (portfolioId, depositId) => {
      await api.delete(`${depositsBase(portfolioId)}/${depositId}`);
    },
  },

  bonds: {
    list: async (portfolioId) => {
      const res = await api.get(bondsBase(portfolioId));
      return res.data.data;
    },

    // Backend-computed coupon schedule (single source): each coupon priced at its own historical per-period rate.
    couponSchedule: async (portfolioId, bondId) => {
      const res = await api.get(`${bondsBase(portfolioId)}/${bondId}/coupon-schedule`);
      return res.data.data;
    },

    add: async (portfolioId, payload) => {
      const res = await api.post(bondsBase(portfolioId), payload);
      return res.data.data;
    },

    update: async (portfolioId, bondId, payload) => {
      const res = await api.put(`${bondsBase(portfolioId)}/${bondId}`, payload);
      return res.data.data;
    },

    sell: async (portfolioId, bondId, payload) => {
      const res = await api.post(`${bondsBase(portfolioId)}/${bondId}/sell`, null, { params: payload });
      return res.data.data;
    },

    reopen: async (portfolioId, bondId) => {
      const res = await api.post(`${bondsBase(portfolioId)}/${bondId}/reopen`);
      return res.data.data;
    },

    delete: async (portfolioId, bondId) => {
      await api.delete(`${bondsBase(portfolioId)}/${bondId}`);
    },
  },
};
