import React, { createContext, useContext, useState, useEffect } from 'react';
import { initKeycloak, getUserInfo, doLogin, doLogout, hasRole, forceRefreshToken } from './keycloak';
const AuthContext = createContext({
  isAuthenticated: false,
  user: null,
  loading: true,
  login: () => {},
  logout: () => {},
  hasRole: () => false,
  refreshUser: () => {},
});
export const AuthProvider = ({ children }) => {
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
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};
