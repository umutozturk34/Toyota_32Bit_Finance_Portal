import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from '../../../shared/components/feedback/Toast';

export default function KeycloakActionToast() {
  const { t } = useTranslation();
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const status = params.get('kc_action_status');
    const action = params.get('kc_action');
    if (!status || !action) return;

    const label = t(`keycloakAction.labels.${action}`, { defaultValue: action });
    if (status === 'success') {
      toast.success(t('keycloakAction.success.title', { label }), t('keycloakAction.success.body'));
    } else if (status === 'cancelled') {
      toast.warning(t('keycloakAction.cancelled.title', { label }), t('keycloakAction.cancelled.body'));
    } else if (status === 'error') {
      toast.error(t('keycloakAction.error.title', { label }), t('keycloakAction.error.body'));
    }

    params.delete('kc_action_status');
    params.delete('kc_action');
    const remaining = params.toString();
    const cleaned = window.location.pathname + (remaining ? `?${remaining}` : '') + window.location.hash;
    window.history.replaceState({}, '', cleaned);
  }, [t]);

  return null;
}
