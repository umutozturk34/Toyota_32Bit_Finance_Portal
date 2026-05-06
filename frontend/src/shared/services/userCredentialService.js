import api from './api';

const PATH = '/user/credentials';

export const userCredentialService = {
  initiatePasswordChange: async (redirectUri) => {
    await api.post(`${PATH}/password/initiate-change`, { redirectUri });
  },

  initiateEmailChange: async (newEmail) => {
    await api.post(`${PATH}/email/initiate-change`, { newEmail });
  },

  confirmEmailChange: async (code) => {
    await api.post(`${PATH}/email/confirm-change`, { code });
  },

  cancelEmailChange: async () => {
    await api.delete(`${PATH}/email/pending`);
  },

  getPendingEmailChange: async () => {
    const response = await api.get(`${PATH}/email/pending`);
    return response.data.data;
  },

  getTwoFactorStatus: async () => {
    const response = await api.get(`${PATH}/2fa`);
    return response.data.data;
  },

  disableTwoFactor: async () => {
    const response = await api.delete(`${PATH}/2fa`);
    return response.data.data;
  },
};
