import { useCallback, useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { UserCog, Sun, Moon, ArrowRight, X } from 'lucide-react';
import i18n from '../../shared/i18n/config';
import { useTheme } from '../../shared/context/useTheme';
import { useUserPreferences, useUpdateUserPreferences } from '../../shared/hooks/useUserPreferences';
import ProductTour from './ProductTour';

function SectionEnter({ children, delay = 0 }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, delay, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </motion.div>
  );
}

function PreferencesStep({ themePreference, setThemePreference, language, onLanguageChange, t }) {
  const themeOptions = [
    { value: 'LIGHT', label: t('onboarding.step.preferences.themeLight'), Icon: Sun },
    { value: 'DARK', label: t('onboarding.step.preferences.themeDark'), Icon: Moon },
  ];
  const langOptions = [
    { value: 'tr', label: 'Türkçe', sub: 'TR' },
    { value: 'en', label: 'English', sub: 'EN' },
  ];

  return (
    <div className="space-y-7">
      <SectionEnter>
        <div className="text-center">
          <div className="mx-auto mb-3 flex items-center justify-center w-12 h-12 rounded-2xl bg-gradient-accent text-white shadow-lg shadow-accent/25">
            <UserCog className="h-5 w-5" />
          </div>
          <h2 className="text-2xl font-display font-bold text-fg">{t('onboarding.step.preferences.title')}</h2>
          <p className="mt-1.5 text-sm text-fg-muted max-w-sm mx-auto">
            {t('onboarding.step.preferences.subtitle')}
          </p>
        </div>
      </SectionEnter>

      <SectionEnter delay={0.08}>
        <div>
          <div className="mb-2 text-[11px] font-mono uppercase tracking-[0.18em] text-fg-subtle">
            {t('onboarding.step.preferences.theme')}
          </div>
          <div className="grid grid-cols-2 gap-3">
            {themeOptions.map(({ value, label, Icon }) => {
              const active = themePreference === value;
              return (
                <button
                  key={value}
                  type="button"
                  onClick={() => setThemePreference(value)}
                  className={`relative overflow-hidden rounded-2xl border px-4 py-4 text-left transition-all duration-200 cursor-pointer ${
                    active
                      ? 'border-accent bg-accent/[0.08] shadow-lg shadow-accent/10'
                      : 'border-border-default bg-bg-elevated hover:border-accent/40 hover:bg-bg-elevated/80'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    <span className={`flex items-center justify-center w-9 h-9 rounded-xl ${
                      active ? 'bg-gradient-accent text-white' : 'bg-surface text-fg-muted'
                    }`}>
                      <Icon className="h-4 w-4" />
                    </span>
                    <span className={`text-sm font-semibold ${active ? 'text-fg' : 'text-fg-muted'}`}>
                      {label}
                    </span>
                  </div>
                  <div
                    aria-hidden="true"
                    className="mt-3 flex items-center gap-1.5"
                  >
                    <span className={`h-2 rounded-full flex-1 ${
                      value === 'LIGHT' ? 'bg-[#0052FF]' : 'bg-[#6366f1]'
                    }`} />
                    <span className={`h-2 rounded-full flex-1 ${
                      value === 'LIGHT' ? 'bg-[#94A3B8]/60' : 'bg-[#8b8b9a]/60'
                    }`} />
                    <span className={`h-2 rounded-full flex-1 ${
                      value === 'LIGHT' ? 'bg-[#E8EDF5]' : 'bg-[#1a1a24]'
                    }`} />
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      </SectionEnter>

      <SectionEnter delay={0.14}>
        <div>
          <div className="mb-2 text-[11px] font-mono uppercase tracking-[0.18em] text-fg-subtle">
            {t('onboarding.step.preferences.language')}
          </div>
          <div className="grid grid-cols-2 gap-3">
            {langOptions.map(({ value, label, sub }) => {
              const active = (language || '').slice(0, 2) === value;
              return (
                <button
                  key={value}
                  type="button"
                  onClick={() => onLanguageChange(value)}
                  className={`flex items-center justify-between rounded-2xl border px-4 py-4 transition-all duration-200 cursor-pointer ${
                    active
                      ? 'border-accent bg-accent/[0.08] shadow-lg shadow-accent/10'
                      : 'border-border-default bg-bg-elevated hover:border-accent/40 hover:bg-bg-elevated/80'
                  }`}
                >
                  <div>
                    <div className={`text-sm font-semibold ${active ? 'text-fg' : 'text-fg-muted'}`}>
                      {label}
                    </div>
                    <div className="mt-0.5 text-[10px] font-mono uppercase tracking-[0.18em] text-fg-subtle">
                      {sub}
                    </div>
                  </div>
                  <span className={`text-[11px] font-mono font-bold tracking-wider ${
                    active ? 'text-accent' : 'text-fg-subtle'
                  }`}>
                    {sub}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      </SectionEnter>
    </div>
  );
}

export default function OnboardingGate() {
  const { t, i18n: i18nInstance } = useTranslation();
  const { preferences, isLoading } = useUserPreferences();
  const updatePreferences = useUpdateUserPreferences();
  const { themePreference, setThemePreference } = useTheme();

  const [phase, setPhase] = useState('preferences');

  const show = !isLoading && preferences && preferences.userSub && preferences.onboardingCompleted === false;

  const handleComplete = useCallback(() => {
    setPhase('done');
    updatePreferences.mutate({ onboardingCompleted: true });
  }, [updatePreferences]);

  const handleLanguageChange = useCallback((lang) => {
    if (i18nInstance.language?.slice(0, 2) !== lang) {
      i18n.changeLanguage(lang);
    }
    if (preferences?.language !== lang) {
      updatePreferences.mutate({ language: lang });
    }
  }, [i18nInstance.language, preferences?.language, updatePreferences]);

  const handleThemeChange = useCallback((next) => {
    if (themePreference !== next) setThemePreference(next);
  }, [themePreference, setThemePreference]);

  const handleStartTour = useCallback(() => {
    setPhase('tour');
  }, []);

  useEffect(() => {
    if (!show) return undefined;
    if (phase !== 'preferences') return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        handleComplete();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [show, phase, handleComplete]);

  if (!show) return null;

  return (
    <>
      <AnimatePresence>
        {phase === 'preferences' && (
          <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="absolute inset-0 bg-bg-base/95"
            />

            <button
              type="button"
              onClick={handleComplete}
              disabled={updatePreferences.isPending}
              className="absolute top-5 right-5 z-10 inline-flex items-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated px-3 py-1.5 text-[12px] text-fg-muted backdrop-blur-md transition-colors hover:text-fg hover:border-accent/40 cursor-pointer disabled:opacity-50"
            >
              <X className="h-3 w-3" />
              {t('onboarding.skip')}
            </button>

            <motion.div
              initial={{ opacity: 0, scale: 0.96, y: 12 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.96, y: 8 }}
              transition={{ type: 'spring', stiffness: 260, damping: 28 }}
              className="relative w-full max-w-[560px] rounded-3xl border border-border-default bg-bg-elevated backdrop-blur-md overflow-hidden shadow-2xl shadow-black/20"
            >
              <span aria-hidden="true" className="pointer-events-none absolute -top-32 left-1/2 -translate-x-1/2 w-[420px] h-56 rounded-full bg-accent/[0.10] blur-[110px]" />
              <span aria-hidden="true" className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/60 to-transparent" />

              <div className="relative px-6 pt-7 pb-2 sm:px-10 sm:pt-9">
                <div className="text-center">
                  <div className="text-[11px] text-fg-subtle">
                    {t('onboarding.tour.phaseLabel')}
                  </div>
                </div>
              </div>

              <div className="relative px-6 pb-6 sm:px-10 sm:pb-8">
                <PreferencesStep
                  themePreference={themePreference}
                  setThemePreference={handleThemeChange}
                  language={i18nInstance.language}
                  onLanguageChange={handleLanguageChange}
                  t={t}
                />
              </div>

              <div className="relative flex items-center justify-end gap-3 border-t border-border-default px-6 py-4 sm:px-10 bg-bg-base/40">
                <motion.button
                  type="button"
                  onClick={handleStartTour}
                  whileHover={{ x: 3, y: -2 }}
                  whileTap={{ scale: 0.97 }}
                  className="inline-flex items-center gap-2 rounded-xl bg-gradient-accent px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-accent/30 transition-all duration-200 border-none cursor-pointer"
                >
                  {t('onboarding.continue')}
                  <motion.span
                    animate={{ x: [0, 4, 0] }}
                    transition={{ duration: 1.2, repeat: Infinity, ease: 'easeInOut' }}
                    className="inline-flex"
                  >
                    <ArrowRight className="h-4 w-4" />
                  </motion.span>
                </motion.button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      <ProductTour
        open={phase === 'tour'}
        onFinish={handleComplete}
        onSkip={handleComplete}
      />
    </>
  );
}
