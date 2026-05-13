import React, { useState, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { initKeycloak, getUserInfo, doLogin, doLogout, hasRole, forceRefreshToken } from './lib/keycloak';
import { AuthContext } from './useAuth';

export const AuthProvider = ({ children }) => {
  const queryClient = useQueryClient();
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setLoading(false);
    }, 5000);
    initKeycloak((auth) => {
      clearTimeout(timeoutId);
      setAuthenticated(auth);
      if (auth) {
        setUser(getUserInfo());
      }
      setLoading(false);
    });
    return () => clearTimeout(timeoutId);
  }, []);
  const login = (options) => {
    doLogin(options);
  };
  const logout = () => {
    queryClient.clear();
    doLogout();
    setAuthenticated(false);
    setUser(null);
  };
  const checkRole = (role) => {
    return hasRole(role);
  };
  const refreshUser = async () => {
    await forceRefreshToken();
    setUser(getUserInfo());
  };
  const value = {
    isAuthenticated: authenticated,
    user,
    loading,
    login,
    logout,
    hasRole: checkRole,
    refreshUser,
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
