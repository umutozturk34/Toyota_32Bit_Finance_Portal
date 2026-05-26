import Keycloak from 'keycloak-js';
import i18n from '../../../shared/i18n/config';
const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'finance-realm',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'finance-frontend',
});

function currentLocale() {
  try {
    const stored = localStorage.getItem('finance-language');
    if (stored === 'tr' || stored === 'en') return stored;
  } catch { /* noop */ }
  const lang = i18n.language || i18n.options.fallbackLng || 'en';
  const short = String(lang).slice(0, 2).toLowerCase();
  return short === 'tr' || short === 'en' ? short : 'en';
}
export const initKeycloak = (onAuthenticatedCallback) => {
  keycloak
    .init({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
      checkLoginIframe: false,
      pkceMethod: 'S256',
      enableLogging: false,
    })
    .then((authenticated) => {
      if (onAuthenticatedCallback) {
        onAuthenticatedCallback(authenticated);
      }
    })
    .catch(() => {});
};
function readLocalTheme() {
  try {
    const stored = localStorage.getItem('finance-theme');
    if (stored === 'light') return 'LIGHT';
    if (stored === 'dark') return 'DARK';
  } catch { /* noop */ }
  try {
    if (window.matchMedia('(prefers-color-scheme: light)').matches) return 'LIGHT';
  } catch { /* noop */ }
  return 'DARK';
}

async function gotoWithLocale(loginOptions, extraParams = {}) {
  const locale = currentLocale();
  loginOptions.locale = locale;
  const url = await keycloak.createLoginUrl(loginOptions);
  const sep = url.includes('?') ? '&' : '?';
  const extras = Object.entries({ kc_locale: locale, ...extraParams })
    .filter(([, v]) => v != null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
    .join('&');
  window.location.href = `${url}${sep}${extras}`;
}
export const doLogin = (options = {}) => {
  const loginOptions = {};
  if (options.redirectUri) {
    loginOptions.redirectUri = options.redirectUri;
  }
  const extras = { themePreference: readLocalTheme() };
  if (options.action === 'register') {
    loginOptions.action = 'register';
  }
  gotoWithLocale(loginOptions, extras);
};
export const doLogout = () => {
  const locale = encodeURIComponent(currentLocale());
  keycloak.logout({ redirectUri: `${window.location.origin}?kc_locale=${locale}` });
};
export const getToken = () => {
  return new Promise((resolve) => {
    if (!keycloak.authenticated) {
      resolve(null);
      return;
    }
    keycloak
      .updateToken(30)
      .then(() => {
        resolve(keycloak.token);
      })
      .catch(() => {
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
export const forceRefreshToken = () => {
  return keycloak.updateToken(-1).then(() => keycloak.token).catch(() => null);
};
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30).catch(() => {
    doLogin();
  });
};
keycloak.onAuthRefreshError = () => {};
export default keycloak;
