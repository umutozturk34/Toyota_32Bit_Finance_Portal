import { motion } from 'framer-motion';
import { Trophy, ArrowLeft } from 'lucide-react';

export default function BeaterPageHeader({ t, backTarget, cameFrom, onBack }) {
  return (
    <header className="pb-3 border-b border-border-default/40">
      {backTarget && (
        <motion.button
          type="button"
          onClick={onBack}
          whileHover={{ x: -2 }}
          whileTap={{ scale: 0.97 }}
          transition={{ type: 'spring', stiffness: 400, damping: 28 }}
          className="group inline-flex items-center gap-2 mb-4 rounded-full border border-border-default/80 bg-bg-elevated/80 backdrop-blur-md pl-1.5 pr-4 py-1.5 text-fg-muted hover:text-accent hover:border-accent/50 hover:bg-accent/10 transition-colors cursor-pointer"
          style={{ boxShadow: '0 2px 12px -4px rgba(0,0,0,0.3)' }}
        >
          <span className="flex items-center justify-center w-7 h-7 rounded-full bg-bg-base/70 border border-border-default/60 group-hover:bg-accent/15 group-hover:border-accent/50 group-hover:shadow-[0_0_10px_-2px_rgba(99,102,241,0.5)] transition-all">
            <ArrowLeft className="h-3.5 w-3.5 transition-transform group-hover:-translate-x-0.5" />
          </span>
          <span className="font-display text-sm font-semibold tracking-tight">
            {t(`analytics.backTo.${cameFrom}`, { defaultValue: 'Geri dön' })}
          </span>
        </motion.button>
      )}
      <div className="flex items-center gap-2.5">
        <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/12 text-accent shrink-0">
          <Trophy className="h-5 w-5" />
        </span>
        <h1 className="font-display text-2xl sm:text-3xl font-bold text-fg tracking-tight leading-none">
          {t('analytics.beaterTitle', { defaultValue: 'Benchmark Yenenler' })}
        </h1>
      </div>
      <p className="mt-2 text-sm text-fg-muted max-w-2xl">
        {t('analytics.beaterSubtitle', {
          defaultValue: 'Bir indikatör seç ve hangi enstrümanların onu geçtiğini gör — TÜFE, politika faizi, mevduat veya başka bir gösterge.',
        })}
      </p>
    </header>
  );
}
