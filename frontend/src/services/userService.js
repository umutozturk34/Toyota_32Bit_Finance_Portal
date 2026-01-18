import api from './api';

export const userService = {
  // Tüm kullanıcıları getir
  getAllUsers: async () => {
    const response = await api.get('/users');
    return response.data;
  },

  // ID ile kullanıcı getir
  getUserById: async (id) => {
    const response = await api.get(`/users/${id}`);
    return response.data;
  },

  // Email ile kullanıcı getir
  getUserByEmail: async (email) => {
    const response = await api.get(`/users/email/${email}`);
    return response.data;
  },

  // Yeni kullanıcı oluştur
  createUser: async (userData) => {
    const response = await api.post('/users', userData);
    return response.data;
  },

  // Kullanıcı güncelle
  updateUser: async (id, userData) => {
    const response = await api.put(`/users/${id}`, userData);
    return response.data;
  },

  // Kullanıcı sil
  deleteUser: async (id) => {
    const response = await api.delete(`/users/${id}`);
    return response.data;
  },
};
