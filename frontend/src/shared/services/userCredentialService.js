import api from './api';

const PATH = '/user/credentials';

export const userCredentialService = {
  initiatePasswordChange: async (redirectUri) => {
    await api.post(`${PATH}/password/initiate-change`, { redirectUri });
  },

  initiateEmailChange: async (newEmail, redirectUri) => {
    await api.post(`${PATH}/email/initiate-change`, { newEmail, redirectUri });
  },
};
