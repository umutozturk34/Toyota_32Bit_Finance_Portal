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
    <div className="mt-5 grid grid-cols-3 gap-2.5">
      {SUMMARY_FEATURES.map(({ key, Icon, labelKey, fallback }, i) => (
        <motion.div
          key={key}
          initial={{ opacity: 0, y: 18, scale: 0.82 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ delay: 0.18 + i * 0.055, type: 'spring', stiffness: 280, damping: 20 }}
          whileHover={{ y: -4, scale: 1.06 }}
          className="flex flex-col items-center justify-center gap-1.5 rounded-xl border border-accent/25 bg-accent/10 px-2 py-3 text-center"
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
            <Icon className="h-5 w-5 text-accent" />
          </motion.div>
          <span className="text-[11px] font-medium text-fg leading-tight">
            {t(labelKey, { defaultValue: fallback })}
          </span>
        </motion.div>
      ))}
    </div>
  );
}
