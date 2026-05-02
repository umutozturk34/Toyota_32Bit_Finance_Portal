import api from './api';

const PATH = '/user/credentials';

export const userCredentialService = {
  initiatePasswordChange: async (redirectUri) => {
    await api.post(`${PATH}/password/initiate-change`, { redirectUri });
  },
};
