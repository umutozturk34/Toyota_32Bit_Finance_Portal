import { motion } from 'framer-motion';
import {
  Bell,
  Eye,
  FileText,
  LineChart,
  PenTool,
  Settings,
  TrendingUp,
  User,
  Wallet,
} from 'lucide-react';
import { EASE_OUT_EXPO } from './constants';

const SUMMARY_FEATURES = [
  { key: 'markets', Icon: TrendingUp, labelKey: 'onboarding.tour.summary.features.markets', fallback: 'Pazarlar' },
  { key: 'portfolio', Icon: Wallet, labelKey: 'onboarding.tour.summary.features.portfolio', fallback: 'Portföy' },
  { key: 'watchlist', Icon: Eye, labelKey: 'onboarding.tour.summary.features.watchlist', fallback: 'Takip listesi' },
  { key: 'notifications', Icon: Bell, labelKey: 'onboarding.tour.summary.features.notifications', fallback: 'Bildirim' },
  { key: 'settings', Icon: Settings, labelKey: 'onboarding.tour.summary.features.settings', fallback: 'Ayarlar' },
  { key: 'profile', Icon: User, labelKey: 'onboarding.tour.summary.features.profile', fallback: 'Profil' },
  { key: 'drawing', Icon: PenTool, labelKey: 'onboarding.tour.summary.features.drawing', fallback: 'Çizim araçları' },
  { key: 'pdf', Icon: FileText, labelKey: 'onboarding.tour.summary.features.pdf', fallback: 'PDF özet' },
  { key: 'scenarios', Icon: LineChart, labelKey: 'onboarding.tour.summary.features.scenarios', fallback: 'Senaryolar' },
];

export function TooltipPointer({ placement, bg }) {
  if (placement === 'center' || placement === 'mobile') return null;
  const base = 'absolute w-3 h-3 rotate-45 border-2 border-accent/30';
  const style = { backgroundColor: bg };
  if (placement === 'bottom') {
    return <span aria-hidden="true" style={style} className={`${base} -top-1.5 left-1/2 -translate-x-1/2 border-r-0 border-b-0`} />;
  }
  if (placement === 'top') {
    return <span aria-hidden="true" style={style} className={`${base} -bottom-1.5 left-1/2 -translate-x-1/2 border-l-0 border-t-0`} />;
  }
  if (placement === 'right') {
    return <span aria-hidden="true" style={style} className={`${base} -left-1.5 top-1/2 -translate-y-1/2 border-r-0 border-t-0`} />;
  }
  if (placement === 'left') {
    return <span aria-hidden="true" style={style} className={`${base} -right-1.5 top-1/2 -translate-y-1/2 border-l-0 border-b-0`} />;
  }
  return null;
}

export function TooltipProgress({ visibleSteps, visibleIndex }) {
  return (
    <div className="flex items-center gap-1 min-w-0 flex-wrap justify-end">
      {visibleSteps.map((s, i) => {
        const active = i === visibleIndex;
        const done = i < visibleIndex;
        const baseCls = 'h-1 rounded-full block';
        const fillCls = active
          ? 'bg-gradient-accent'
          : done
            ? 'bg-accent/50'
            : 'bg-border-default';
        return (
          <motion.span
            key={s.id}
            layout
            aria-hidden="true"
            className={`${baseCls} ${fillCls}`}
            animate={
              active
                ? {
                    width: 18,
                    boxShadow: [
                      '0 0 0 0 rgba(99,102,241,0.0)',
                      '0 0 10px 1px rgba(99,102,241,0.55)',
                      '0 0 0 0 rgba(99,102,241,0.0)',
                    ],
                  }
                : { width: 4, boxShadow: '0 0 0 0 rgba(0,0,0,0)' }
            }
            transition={
              active
                ? {
                    width: { duration: 0.35, ease: EASE_OUT_EXPO },
                    boxShadow: { duration: 1.8, ease: 'easeInOut', repeat: Infinity },
                  }
                : { width: { duration: 0.35, ease: EASE_OUT_EXPO } }
            }
          />
        );
      })}
    </div>
  );
}

export function SummaryTitle({ t, titleKey }) {
  return (
    <div className="relative flex items-center justify-center">
      <motion.span
        aria-hidden="true"
        className="absolute -left-1 -top-1 text-base"
        animate={{
          scale: [0, 1.2, 0.8, 1.2, 0],
          rotate: [0, 90, 180, 270, 360],
          opacity: [0, 1, 1, 1, 0],
        }}
        transition={{ delay: 0.4, duration: 2.4, repeat: Infinity, repeatDelay: 1.6, ease: 'easeInOut' }}
      >
        ✨
      </motion.span>
      <motion.span
        aria-hidden="true"
        className="absolute -right-2 top-1 text-sm"
        animate={{
          scale: [0, 1.1, 0.7, 1.1, 0],
          rotate: [0, -90, -180, -270, -360],
          opacity: [0, 1, 1, 1, 0],
        }}
        transition={{ delay: 0.9, duration: 2.4, repeat: Infinity, repeatDelay: 1.6, ease: 'easeInOut' }}
      >
        ✨
      </motion.span>
      <motion.h3
        initial={{ opacity: 0, y: -10, scale: 0.9 }}
        animate={{
          opacity: 1,
          y: 0,
          scale: 1,
          backgroundPosition: ['0% 50%', '200% 50%'],
        }}
        transition={{
          opacity: { duration: 0.55, ease: EASE_OUT_EXPO },
          y: { duration: 0.55, ease: EASE_OUT_EXPO },
          scale: { duration: 0.55, ease: EASE_OUT_EXPO },
          backgroundPosition: { delay: 0.6, duration: 3.6, repeat: Infinity, ease: 'linear' },
        }}
        className="font-display text-2xl landscape:text-xl sm:text-3xl sm:landscape:text-2xl font-bold leading-tight tracking-tight text-center bg-gradient-to-r from-accent via-fuchsia-400 to-accent-bright bg-clip-text text-transparent"
        style={{ backgroundSize: '200% 100%' }}
      >
        {t(titleKey ?? '')}
      </motion.h3>
    </div>
  );
}

export function SummaryGrid({ t }) {
  return (
    <div className="mt-5 landscape:mt-3 grid grid-cols-3 gap-1.5 landscape:gap-1 sm:gap-2.5 sm:landscape:gap-2">
      {SUMMARY_FEATURES.map(({ key, Icon, labelKey, fallback }, i) => (
        <motion.div
          key={key}
          initial={{ opacity: 0, y: 18, scale: 0.82 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ delay: 0.18 + i * 0.055, type: 'spring', stiffness: 280, damping: 20 }}
          whileHover={{ y: -4, scale: 1.06 }}
          className="flex flex-col items-center justify-start gap-1 landscape:gap-0.5 sm:gap-1.5 sm:landscape:gap-1 rounded-xl border border-accent/25 bg-accent/10 px-1.5 py-2.5 landscape:px-1.5 landscape:py-1.5 sm:px-2 sm:py-3 sm:landscape:px-2 sm:landscape:py-2 text-center min-h-[72px] landscape:min-h-[52px] sm:min-h-[84px] sm:landscape:min-h-[60px]"
          style={{ backgroundColor: 'rgba(99,102,241,0.08)' }}
        >
          <motion.div
            initial={{ rotate: -18, scale: 0.6 }}
            animate={{
              rotate: 0,
              scale: 1,
              y: [0, -3, 0],
            }}
            transition={{
              rotate: { delay: 0.32 + i * 0.055, type: 'spring', stiffness: 360, damping: 18 },
              scale: { delay: 0.32 + i * 0.055, type: 'spring', stiffness: 360, damping: 18 },
              y: { delay: 1 + i * 0.16, duration: 2.2 + (i % 3) * 0.3, repeat: Infinity, ease: 'easeInOut' },
            }}
          >
            <Icon className="h-4 w-4 landscape:h-3.5 landscape:w-3.5 sm:h-5 sm:w-5 sm:landscape:h-4 sm:landscape:w-4 text-accent" />
          </motion.div>
          <span className="text-[10px] landscape:text-[9px] sm:text-[11px] sm:landscape:text-[10px] font-medium text-fg leading-tight landscape:leading-[1.1] break-words hyphens-auto">
            {t(labelKey, { defaultValue: fallback })}
          </span>
        </motion.div>
      ))}
    </div>
  );
}
