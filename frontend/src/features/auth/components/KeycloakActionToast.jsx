import { useEffect } from 'react';
import { toast } from '../../../shared/components/Toast';

const ACTION_LABELS = {
  UPDATE_PASSWORD: 'Şifre',
  CONFIGURE_TOTP: 'İki Adımlı Doğrulama',
  UPDATE_PROFILE: 'Profil',
  delete_account: 'Hesap Silme',
};

const STATUS_HANDLERS = {
  success: (label) => toast.success(`${label} güncellendi`, 'İşlem başarılı'),
  cancelled: (label) => toast.warning(`${label} iptal edildi`, 'Hiçbir değişiklik yapılmadı'),
  error: (label) => toast.error(`${label} güncellenemedi`, 'İşlem sırasında bir hata oluştu'),
};

export default function KeycloakActionToast() {
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const status = params.get('kc_action_status');
    const action = params.get('kc_action');
    if (!status || !action) return;

    const label = ACTION_LABELS[action] || action;
    const handler = STATUS_HANDLERS[status];
    if (handler) handler(label);

    params.delete('kc_action_status');
    params.delete('kc_action');
    const remaining = params.toString();
    const cleaned = window.location.pathname + (remaining ? `?${remaining}` : '') + window.location.hash;
    window.history.replaceState({}, '', cleaned);
  }, []);

  return null;
}
