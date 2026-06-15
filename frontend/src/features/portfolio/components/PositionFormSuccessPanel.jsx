import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { ShieldCheck } from 'lucide-react';
import { Check } from '../../../shared/components/feedback/AnimatedIcons';

export default function PositionFormSuccessPanel({ title, subtitle }) {
  const { t } = useTranslation();
  return (
    <motion.div
      initial={{ scale: 0.85, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      className="mx-auto flex max-w-sm flex-col items-center justify-center gap-3 py-12"
    >
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ type: 'spring', stiffness: 300, damping: 20 }}
        className="flex items-center justify-center w-16 h-16 rounded-full bg-success/15"
      >
        <Check className="h-8 w-8 text-success" strokeWidth={2.5} />
      </motion.div>
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="text-center space-y-1"
      >
        <p className="text-sm font-semibold text-fg">{title}</p>
        <p className="text-xs text-fg-muted">{subtitle}</p>
      </motion.div>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.35 }}
        className="flex items-center gap-1.5 text-[11px] text-success/70"
      >
        <ShieldCheck className="h-3.5 w-3.5" />
        {t('positionForm.success.completed')}
      </motion.div>
    </motion.div>
  );
}
