import { useState, useEffect } from 'react';
import { ListPlus, Tag } from 'lucide-react';
import BaseModal from '../../../../shared/components/BaseModal';
import { useCreateWatchlist } from '../../../../shared/hooks/useWatchlist';
import { toast } from '../../../../shared/components/Toast';
import { extractApiError } from '../../../../shared/utils/apiError';

export default function CreateWatchlistModal({ isOpen, onClose, onCreated }) {
  const create = useCreateWatchlist();
  const [name, setName] = useState('');

  useEffect(() => {
    if (isOpen) setName('');
  }, [isOpen]);

  const submit = async (e) => {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      toast.error('Liste adı gerekli');
      return;
    }
    try {
      const created = await create.mutateAsync(trimmed);
      toast.success(`"${created.name}" oluşturuldu`);
      onCreated?.(created);
      onClose();
    } catch (err) {
      toast.error(extractApiError(err, 'Liste oluşturulamadı'));
    }
  };

  return (
    <BaseModal
      isOpen={isOpen}
      onClose={onClose}
      icon={ListPlus}
      title="Yeni liste"
      subtitle="Maks. 20 liste oluşturabilirsin"
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="space-y-1.5">
          <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
            <Tag className="h-3 w-3" />
            Liste Adı
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={64}
            autoFocus
            placeholder="Crypto Yatırımları"
            className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
        </div>

        <button
          type="submit"
          disabled={create.isPending}
          className="w-full flex items-center justify-center gap-2 rounded-lg py-3 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer disabled:opacity-50"
        >
          {create.isPending ? 'Oluşturuluyor…' : 'Oluştur'}
        </button>
      </form>
    </BaseModal>
  );
}
