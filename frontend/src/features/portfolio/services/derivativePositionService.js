import api from '../../../shared/services/api';

const base = (portfolioId) => `/portfolios/${portfolioId}/derivatives`;

export const derivativePositionService = {
  list: async (portfolioId, { openOnly = false } = {}) => {
    const res = await api.get(base(portfolioId), { params: { openOnly } });
    return res.data.data;
  },

  open: async (portfolioId, payload) => {
    const res = await api.post(base(portfolioId), payload);
    return res.data.data;
  },

  close: async (portfolioId, positionId, payload) => {
    const res = await api.patch(`${base(portfolioId)}/${positionId}/close`, payload);
    return res.data.data;
  },

  updateClose: async (portfolioId, positionId, payload) => {
    const res = await api.put(`${base(portfolioId)}/${positionId}/close`, payload);
    return res.data.data;
  },

  update: async (portfolioId, positionId, payload) => {
    const res = await api.put(`${base(portfolioId)}/${positionId}`, payload);
    return res.data.data;
  },

  reopen: async (portfolioId, positionId) => {
    const res = await api.patch(`${base(portfolioId)}/${positionId}/reopen`);
    return res.data.data;
  },

  remove: async (portfolioId, positionId) => {
    await api.delete(`${base(portfolioId)}/${positionId}`);
  },
};
