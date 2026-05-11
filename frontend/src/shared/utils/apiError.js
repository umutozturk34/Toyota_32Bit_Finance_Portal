import i18n from '../i18n/config';

const STATUS_KEY = {
  502: 'error.serverUnavailable',
  503: 'error.serverUnavailable',
  504: 'error.gatewayTimeout',
};

export function extractApiError(err, fallback) {
  const response = err?.response;
  const status = response?.status;
  const data = response?.data;

  if (data && typeof data === 'object') {
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
