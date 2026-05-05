export function extractApiError(err, fallback = 'İşlem başarısız') {
  const data = err?.response?.data;
  if (data && typeof data === 'object') {
    if (typeof data.message === 'string' && data.message.length > 0) return data.message;
    if (typeof data.error === 'string' && data.error.length > 0) return data.error;
    if (data.error && typeof data.error === 'object' && typeof data.error.message === 'string') {
      return data.error.message;
    }
    if (data.errors && typeof data.errors === 'object') {
      const firstField = Object.values(data.errors)[0];
      if (typeof firstField === 'string' && firstField.length > 0) return firstField;
    }
  }
  if (typeof err?.message === 'string' && err.message.length > 0) return err.message;
  return fallback;
}
