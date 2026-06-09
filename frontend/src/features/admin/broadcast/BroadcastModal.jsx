import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Megaphone } from 'lucide-react';
import { useBroadcast } from './useBroadcast';
import { toast } from '../../../shared/components/feedback/toastBus';
import { toastApiError } from '../../../shared/utils/apiError';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';

export default function BroadcastModal({ open, onClose }) {
  const { t } = useTranslation();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [wasOpen, setWasOpen] = useState(open);
  const broadcast = useBroadcast();

  if (open && !wasOpen) {
    setWasOpen(true);
    setTitle('');
    setBody('');
  } else if (!open && wasOpen) {
    setWasOpen(false);
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim() || !body.trim() || broadcast.isPending) return;
    try {
      const result = await broadcast.mutateAsync({ title: title.trim(), body: body.trim() });
      toast.success(t('broadcast.successTitle'), t('broadcast.successBody', { dispatched: result.dispatched, total: result.totalRecipients }));
      onClose?.();
    } catch (err) {
      toastApiError(err, t('broadcast.failed'));
    }
  };

  return (
    <BaseModal
      isOpen={open}
      onClose={onClose}
      icon={Megaphone}
      title={t('broadcast.title')}
      subtitle={t('broadcast.subtitle')}
      size="md"
    >
      <form onSubmit={handleSubmit} className="space-y-3">
        <label className="block">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">{t('broadcast.titleLabel')}</span>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={120}
            required
            className="mt-1.5 w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm text-fg focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all"
          />
        </label>
        <label className="block">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">{t('broadcast.bodyLabel')}</span>
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            rows={4}
            maxLength={2000}
            required
            className="mt-1.5 w-full resize-none rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm text-fg focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all"
          />
        </label>

        <div className="flex gap-2 pt-2">
          <Button
            type="button"
            variant="secondary"
            size="lg"
            fullWidth
            onClick={onClose}
            disabled={broadcast.isPending}
          >
            {t('common.cancel')}
          </Button>
          <Button
            type="submit"
            variant="gradient"
            size="lg"
            fullWidth
            loading={broadcast.isPending}
            leftIcon={<Megaphone className="h-3.5 w-3.5" />}
            motionPreset="tap"
          >
            {broadcast.isPending ? t('broadcast.sending') : t('broadcast.publishCta')}
          </Button>
        </div>
      </form>
    </BaseModal>
  );
}
