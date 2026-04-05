import Keycloak from 'keycloak-js';
const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'finance-realm',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'finance-frontend',
});
export const initKeycloak = (onAuthenticatedCallback) => {
  keycloak
    .init({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
      checkLoginIframe: false,
      pkceMethod: 'S256',
      enableLogging: true,
    })
    .then((authenticated) => {
      if (authenticated) {
        console.log('User authenticated');
        console.log('Username:', keycloak.tokenParsed?.preferred_username);
        console.log('Email:', keycloak.tokenParsed?.email);
        console.log('Roles:', keycloak.tokenParsed?.realm_access?.roles);
      } else {
        console.log('User not authenticated');
      }
      if (onAuthenticatedCallback) {
        onAuthenticatedCallback(authenticated);
      }
    })
    .catch((error) => {
      console.error('Keycloak initialization error:', error);
    });
};
export const doLogin = (options = {}) => {
  const loginOptions = {};
  if (options.redirectUri) {
    loginOptions.redirectUri = options.redirectUri;
  }
  if (options.action === 'register') {
    loginOptions.action = 'register';
  }
  keycloak.login(loginOptions);
};
export const doLogout = () => {
  keycloak.logout({ redirectUri: window.location.origin });
};
export const getToken = () => {
  return new Promise((resolve, reject) => {
    if (!keycloak.authenticated) {
      resolve(null);
      return;
    }
    keycloak
      .updateToken(30)
      .then(() => {
        resolve(keycloak.token);
      })
      .catch((error) => {
        console.error('Token refresh failed:', error);
        resolve(null);
      });
  });
};
export const getUserInfo = () => {
  if (!keycloak.tokenParsed) return null;
  return {
    id: keycloak.tokenParsed.sub,
    username: keycloak.tokenParsed.preferred_username,
    email: keycloak.tokenParsed.email,
    firstName: keycloak.tokenParsed.given_name,
    lastName: keycloak.tokenParsed.family_name,
    roles: keycloak.tokenParsed.realm_access?.roles || [],
  };
};
export const hasRole = (role) => {
  return keycloak.hasRealmRole(role);
};
export const isAuthenticated = () => {
  return keycloak.authenticated;
};
keycloak.onTokenExpired = () => {
  console.log('Token expired, refreshing...');
  keycloak.updateToken(30).catch(() => {
    console.error('Token refresh failed, redirecting to login...');
    doLogin();
  });
};
keycloak.onAuthSuccess = () => {
  console.log('Authentication successful');
};
keycloak.onAuthError = (error) => {
  console.error('Authentication error:', error);
};
keycloak.onAuthRefreshSuccess = () => {
  console.log('Token refreshed successfully');
};
keycloak.onAuthRefreshError = () => {
  console.error('Token refresh failed');
};
export default keycloak;
