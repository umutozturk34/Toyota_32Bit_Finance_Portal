import React, { useState, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { initKeycloak, getUserInfo, doLogin, doLogout, hasRole, forceRefreshToken } from './lib/keycloak';
import { AuthContext } from './useAuth';
import { AUTH_REFRESH_INTERVAL_MS } from '../../shared/constants/timings';

// Per-user UI state (useSessionState → sessionStorage 'ss:*': compare selections, portfolio tabs, beater
// filters, …) is NOT user-scoped, and sessionStorage outlives a logout→login in the SAME tab — so without
// this one account's selections (e.g. a portfolio + CPI in Compare) leak into the next account's session.
// Cleared on logout and whenever a different user authenticates in this tab.
function clearUiSessionState() {
  try {
    const keys = [];
    for (let i = 0; i < sessionStorage.length; i += 1) {
      const key = sessionStorage.key(i);
      if (key && key.startsWith('ss:')) keys.push(key);
    }
    keys.forEach((key) => sessionStorage.removeItem(key));
  } catch { /* sessionStorage unavailable */ }
}

export const AuthProvider = ({ children }) => {
  const queryClient = useQueryClient();
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setLoading(false);
    }, AUTH_REFRESH_INTERVAL_MS);
    initKeycloak((auth) => {
      clearTimeout(timeoutId);
      setAuthenticated(auth);
      if (auth) {
        const info = getUserInfo();
        // A different user than the one whose UI state is cached in this tab → wipe it so the previous
        // account's compare/portfolio selections don't carry over (logout→login or SSO account switch).
        const prevSub = localStorage.getItem('last-auth-sub');
        if (info?.id && prevSub && prevSub !== info.id) clearUiSessionState();
        if (info?.id) localStorage.setItem('last-auth-sub', info.id);
        setUser(info);
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
    clearUiSessionState();
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
