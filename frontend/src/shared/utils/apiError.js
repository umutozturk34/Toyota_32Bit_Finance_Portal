import i18n from '../i18n/config';
import { toast } from '../components/feedback/toastBus';

const STATUS_KEY = {
  502: 'error.serverUnavailable',
  503: 'error.serverUnavailable',
  504: 'error.gatewayTimeout',
};

export function isValidationError(err) {
  const r = err?.response;
  return r?.status === 400 && r?.data?.errorCode === 'VALIDATION_ERROR';
}

export function extractApiError(err, fallback) {
  const response = err?.response;
  const status = response?.status;
  const data = response?.data;

  if (data && typeof data === 'object') {
    if (data.errorCode === 'VALIDATION_ERROR') return i18n.t('error.validationFailed');
    const fieldErrors = data.validationErrors || data.errors;
    if (fieldErrors && typeof fieldErrors === 'object') {
      const firstField = Object.values(fieldErrors)[0];
      if (typeof firstField === 'string' && firstField.length > 0) return firstField;
    }
    const isValidationCode = data.errorCode === 'VALIDATION_ERROR';
    if (typeof data.message === 'string' && data.message.length > 0 && !isValidationCode) {
      return data.message;
    }
    if (typeof data.error === 'string' && data.error.length > 0) return data.error;
    if (data.error && typeof data.error === 'object' && typeof data.error.message === 'string') {
      return data.error.message;
    }
    if (typeof data.message === 'string' && data.message.length > 0) return data.message;
  }

  if (STATUS_KEY[status]) return i18n.t(STATUS_KEY[status]);
  if (status >= 500) return i18n.t('error.serverError');
  if (!response) return i18n.t('error.networkError');

  return fallback ?? i18n.t('error.actionFailed');
}

// True when the global axios response interceptor already surfaced this error to the user (a validation/server/
// gateway/network toast, the rate-limit toast, or the 401 login redirect). Component catch handlers check this
// so a single failure shows ONE toast, not the interceptor's plus their own.
export function isGloballyToasted(err) {
  const r = err?.response;
  if (!r) return true;
  const s = r.status;
  if (s === 401 || s === 429 || s >= 500) return true;
  return s === 400 && r.data?.errorCode === 'VALIDATION_ERROR';
}

// Toast an API error from a component catch, but stay silent for errors the interceptor already toasted — use
// this instead of `toast.error(extractApiError(...))` so form validation (400) shows just the global toast.
// Business errors the interceptor ignores (e.g. 409 "limit reached") still toast, falling back to `fallback`.
export function toastApiError(err, fallback) {
  if (isGloballyToasted(err)) return;
  toast.error(extractApiError(err, fallback));
}
