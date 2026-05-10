import { Send, Loader2, ShieldOff } from 'lucide-react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { MAX_BODY } from '../util';

export default function Composer({ value, onChange, onSubmit, disabled, placeholder, pending, hint }) {
  const { t } = useTranslation();
  return (
    <form
      onSubmit={onSubmit}
      className="relative border-t border-border-default bg-gradient-to-b from-transparent to-accent/[0.03] px-4 pt-3 pb-2.5 shrink-0"
    >
      <div className="flex items-end gap-2">
        {hint ? (
          <div className="flex-1 flex items-center gap-2 text-[11px] text-warning bg-warning/10 border border-warning/30 rounded-xl px-3 py-2.5">
            <ShieldOff className="h-3.5 w-3.5 shrink-0" />
            <span>{hint}</span>
          </div>
        ) : (
          <textarea
            value={value}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                onSubmit(e);
              }
            }}
            placeholder={placeholder}
            rows={2}
            maxLength={MAX_BODY}
            className="flex-1 resize-none rounded-xl border border-border-default bg-bg-base/60 px-3 py-2.5 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.15)] focus:bg-bg-base transition-all"
          />
        )}
        <motion.button
          type="submit"
          disabled={disabled}
          whileTap={{ scale: 0.94 }}
          whileHover={{ y: -1 }}
          transition={{ type: 'spring', stiffness: 400, damping: 25 }}
          className="relative shrink-0 flex items-center justify-center w-11 h-11 rounded-xl text-white overflow-hidden disabled:opacity-40 disabled:cursor-not-allowed border-none cursor-pointer shadow-lg shadow-accent/30"
        >
          <span aria-hidden className="absolute inset-0 bg-gradient-to-br from-accent via-accent-bright to-accent" />
          {pending ? <Loader2 className="relative h-4 w-4 animate-spin" /> : <Send className="relative h-4 w-4" />}
        </motion.button>
      </div>
      {!hint && (
        <div className="mt-1.5 flex items-center justify-between text-[10px] font-mono text-fg-subtle px-1">
          <span>{t('adminMessage.hint')}</span>
          <span className={value.length > MAX_BODY - 200 ? 'text-warning' : ''}>{value.length}/{MAX_BODY}</span>
        </div>
      )}
    </form>
  );
}
