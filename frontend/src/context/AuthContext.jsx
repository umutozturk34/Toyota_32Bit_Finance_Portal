import React, { createContext, useContext, useState, useEffect } from 'react';
import { initKeycloak, isAuthenticated, getUserInfo, doLogin, doLogout, hasRole } from '../services/keycloak';

const AuthContext = createContext({
  isAuthenticated: false,
  user: null,
  loading: true,
  login: () => {},
  logout: () => {},
  hasRole: () => false,
});

export const AuthProvider = ({ children }) => {
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    initKeycloak((auth) => {
      setAuthenticated(auth);
      
      if (auth) {
        const userInfo = getUserInfo();
        setUser(userInfo);
        
        console.log('👤 User Info:', userInfo);
        console.log('🔑 Has ADMIN role:', hasRole('ADMIN'));
        console.log('🔑 Has USER role:', hasRole('USER'));
      }
      
      setLoading(false);
    });
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

  const value = {
    isAuthenticated: authenticated,
    user,
    loading,
    login,
    logout,
    hasRole: checkRole,
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
