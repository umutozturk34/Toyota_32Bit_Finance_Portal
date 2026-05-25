import { motion, AnimatePresence } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { Wallet, ShieldCheck, Sparkles } from 'lucide-react';
import { Check, AlertTriangle } from '../../../shared/components/feedback/AnimatedIcons';
import PageHeader from '../../../shared/components/layout/PageHeader';
import ProcessingSteps from '../../../shared/components/feedback/ProcessingSteps';
import Card from '../../../shared/components/card';
import useProcessingAnimation from '../../../shared/hooks/useProcessingAnimation';
import { useCreatePortfolio, useInvalidatePortfolio } from '../hooks/usePortfolioData';
import { ONBOARDING_SUCCESS_HOLD_MS } from '../../../shared/constants/timings';

export default function PortfolioOnboardingHost({
  phase,
  setPhase,
  name,
  setName,
}) {
  const { t } = useTranslation();
  const invalidatePortfolio = useInvalidatePortfolio();
  const createPortfolio = useCreatePortfolio();
  const { processingStep, runAnimation, reset: resetOnboarding } = useProcessingAnimation();

  const defaultPortfolioName = t('portfolio.onboarding.defaultName');
  const onboardingSteps = [
    { label: t('portfolio.onboarding.steps.verifying'), duration: 600 },
    { label: t('portfolio.onboarding.steps.creating'), duration: 700 },
    { label: t('portfolio.onboarding.steps.preparing'), duration: 600 },
  ];

  const handleStart = () => {
    setName(defaultPortfolioName);
    setPhase('confirm');
  };
  const handleCancel = () => setPhase('idle');

  const handleCreate = async () => {
    const trimmedName = name.trim() || defaultPortfolioName;
    setPhase('processing');
    try {
      await Promise.all([
        createPortfolio.mutateAsync(trimmedName),
        runAnimation(onboardingSteps),
      ]);
      setPhase('success');
      setTimeout(() => {
        invalidatePortfolio();
        setPhase('idle');
      }, ONBOARDING_SUCCESS_HOLD_MS);
    } catch {
      resetOnboarding();
      setPhase('idle');
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader icon={<Wallet className="h-5 w-5" />} title={t('portfolio.headerTitle')} />
      <Card
        as={motion.div}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        variant="elevated"
        radius="xl"
        padding="xl"
        backdropBlur
        className="flex flex-col items-center justify-center gap-5 min-h-[320px]"
      >
        <AnimatePresence mode="wait">
          {phase === 'success' && (
            <motion.div
              key="success"
              initial={{ scale: 0.92, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.92, opacity: 0 }}
              transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
              className="flex flex-col items-center gap-3 relative"
            >
              <motion.div
                className="absolute top-0 w-32 h-32 rounded-full bg-success/20 blur-3xl pointer-events-none"
                initial={{ scale: 0, opacity: 0 }}
                animate={{ scale: [0, 1.4, 1], opacity: [0, 0.9, 0.5] }}
                transition={{ duration: 1, ease: 'easeOut' }}
              />
              <div className="relative">
                <motion.div
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  transition={{ type: 'spring', stiffness: 300, damping: 20 }}
                  className="flex items-center justify-center w-16 h-16 rounded-full bg-success/15"
                >
                  <Check className="h-8 w-8 text-success" strokeWidth={2.5} />
                </motion.div>
                {[0, 1, 2, 3, 4, 5].map((i) => {
                  const angle = (i / 6) * Math.PI * 2;
                  return (
                    <motion.span
                      key={i}
                      className="absolute top-1/2 left-1/2 w-1.5 h-1.5 rounded-full bg-success/60"
                      initial={{ x: 0, y: 0, opacity: 0, scale: 0 }}
                      animate={{
                        x: Math.cos(angle) * 38,
                        y: Math.sin(angle) * 38,
                        opacity: [0, 1, 0],
                        scale: [0, 1, 0.4],
                      }}
                      transition={{ duration: 0.7, delay: 0.18 + i * 0.04, ease: 'easeOut' }}
                    />
                  );
                })}
              </div>
              <motion.p
                className="text-base font-semibold text-fg"
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.18, duration: 0.25 }}
              >
                {t('portfolio.onboarding.successTitle')}
              </motion.p>
              <motion.div
                className="flex items-center gap-1.5 text-[11px] text-success/70"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.28, duration: 0.25 }}
              >
                <ShieldCheck className="h-3.5 w-3.5" />
                {t('portfolio.onboarding.successHint')}
              </motion.div>
            </motion.div>
          )}
          {phase === 'processing' && (
            <motion.div
              key="processing"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.18 }}
              className="w-full"
            >
              <ProcessingSteps steps={onboardingSteps} currentStep={processingStep} />
            </motion.div>
          )}
          {phase === 'confirm' && (
            <motion.div
              key="confirm"
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
              className="w-full max-w-sm space-y-5"
            >
              <div className="flex flex-col items-center gap-3">
                <motion.div
                  className="flex items-center justify-center w-12 h-12 rounded-full bg-warning/10"
                  animate={{ scale: [1, 1.08, 1] }}
                  transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut' }}
                >
                  <AlertTriangle className="h-6 w-6 text-warning" />
                </motion.div>
                <motion.div
                  className="text-center space-y-1"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ delay: 0.1, duration: 0.25 }}
                >
                  <p className="text-sm font-semibold text-fg">{t('portfolio.onboarding.confirmTitle')}</p>
                  <p className="text-xs text-fg-muted">{t('portfolio.onboarding.confirmHint')}</p>
                </motion.div>
              </div>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder={defaultPortfolioName}
                maxLength={64}
                className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent"
              />
              <motion.div
                className="flex gap-2"
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.18, duration: 0.25 }}
              >
                <motion.button
                  onClick={handleCancel}
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.97 }}
                  className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-colors cursor-pointer"
                >
                  {t('common.cancel')}
                </motion.button>
                <motion.button
                  onClick={handleCreate}
                  whileHover={{ scale: 1.02, y: -1 }}
                  whileTap={{ scale: 0.97 }}
                  className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-accent shadow-md shadow-accent/20 hover:bg-accent-bright transition-colors border-none cursor-pointer"
                >
                  <Wallet className="h-4 w-4" />
                  {t('common.confirm')}
                </motion.button>
              </motion.div>
            </motion.div>
          )}
          {phase === 'idle' && (
            <motion.div
              key="idle"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
              className="flex flex-col items-center gap-5 relative"
            >
              <motion.div
                className="absolute -top-4 w-32 h-32 rounded-full bg-accent/15 blur-3xl pointer-events-none"
                animate={{ scale: [1, 1.15, 1], opacity: [0.6, 0.85, 0.6] }}
                transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut' }}
              />
              <motion.div
                className="relative flex items-center justify-center w-16 h-16 rounded-2xl bg-accent/10"
                animate={{ y: [0, -4, 0] }}
                transition={{ duration: 3, repeat: Infinity, ease: 'easeInOut' }}
              >
                <Wallet className="w-8 h-8 text-accent" />
                <motion.span
                  className="absolute -top-1 -right-1"
                  animate={{ rotate: [0, 12, -8, 0], scale: [1, 1.15, 0.95, 1] }}
                  transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
                >
                  <Sparkles className="w-3.5 h-3.5 text-warning" />
                </motion.span>
              </motion.div>
              <motion.div
                className="text-center space-y-2"
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.12, duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
              >
                <h2 className="text-xl font-semibold text-fg">{t('portfolio.onboarding.idleTitle')}</h2>
                <p className="text-sm text-fg-muted max-w-md">
                  {t('portfolio.onboarding.idleSubtitle')}
                </p>
              </motion.div>
              <motion.button
                onClick={handleStart}
                whileHover={{ scale: 1.04, y: -1 }}
                whileTap={{ scale: 0.96 }}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.22, duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
                className="flex items-center gap-2 rounded-lg bg-accent px-6 py-2.5 text-sm font-semibold text-white shadow-lg shadow-accent/20 hover:bg-accent-bright transition-colors border-none cursor-pointer"
              >
                <Wallet className="h-4 w-4" />
                {t('portfolio.onboarding.startCta')}
              </motion.button>
            </motion.div>
          )}
        </AnimatePresence>
      </Card>
    </div>
  );
}
