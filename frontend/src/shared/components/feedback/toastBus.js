import i18n from '../../i18n/config';

let _fireToast = null;

export function _registerToastFireHandler(handler) {
  _fireToast = handler;
}

export function toast(type, title, message, options = {}) {
  _fireToast?.({ type, title, message, ...options });
}

toast.success = (title, message) => toast('success', title, message);
toast.error = (title, message, options) => toast('error', title, message, options);
toast.warning = (title, message) => toast('warning', title, message);
toast.info = (title, message) => toast('info', title, message);
toast.rateLimit = (message, retryAfter) => toast('rateLimit', i18n.t('toast.rateLimitTitle'), message, { retryAfter, dedupeKey: 'rateLimit' });
