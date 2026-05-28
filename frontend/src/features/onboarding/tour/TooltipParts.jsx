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
