import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { UserCog, Sun, Moon, ArrowRight, X, TrendingUp } from 'lucide-react';
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
    <div className="space-y-5 sm:space-y-7">
      <SectionEnter>
        <div className="text-center">
          <div className="mx-auto mb-3 flex items-center justify-center w-12 h-12 rounded-2xl bg-gradient-accent text-white shadow-lg shadow-accent/25">
            <UserCog className="h-5 w-5" />
          </div>
          <h2 className="text-xl sm:text-2xl font-display font-bold text-fg">{t('onboarding.step.preferences.title')}</h2>
          <p className="mt-1.5 text-xs sm:text-sm text-fg-muted max-w-sm mx-auto">
            {t('onboarding.step.preferences.subtitle')}
          </p>
        </div>
      </SectionEnter>

      <SectionEnter delay={0.08}>
        <div>
          <div className="mb-2 text-[11px] font-mono uppercase tracking-[0.18em] text-fg-subtle">
            {t('onboarding.step.preferences.theme')}
          </div>
          <div className="grid grid-cols-2 gap-2 sm:gap-3">
            {themeOptions.map(({ value, label, Icon }) => {
              const active = themePreference === value;
              return (
                <button
                  key={value}
                  type="button"
                  onClick={() => setThemePreference(value)}
                  className={`relative overflow-hidden rounded-2xl border px-3 py-3 sm:px-4 sm:py-4 text-left transition-all duration-200 cursor-pointer min-h-[88px] ${
                    active
                      ? 'border-accent bg-accent/[0.08] shadow-lg shadow-accent/10'
                      : 'border-border-default bg-bg-elevated hover:border-accent/40 hover:bg-bg-elevated/80'
                  }`}
                >
                  <div className="flex items-center gap-2 sm:gap-2.5 min-w-0">
                    <span className={`flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-xl shrink-0 ${
                      active ? 'bg-gradient-accent text-white' : 'bg-surface text-fg-muted'
                    }`}>
                      <Icon className="h-4 w-4" />
                    </span>
                    <span className={`text-xs sm:text-sm font-semibold truncate ${active ? 'text-fg' : 'text-fg-muted'}`}>
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
          <div className="grid grid-cols-2 gap-2 sm:gap-3">
            {langOptions.map(({ value, label, sub }) => {
              const active = (language || '').slice(0, 2) === value;
              return (
                <button
                  key={value}
                  type="button"
                  onClick={() => onLanguageChange(value)}
                  className={`flex items-center justify-between gap-2 rounded-2xl border px-3 py-3 sm:px-4 sm:py-4 min-h-[60px] transition-all duration-200 cursor-pointer ${
                    active
                      ? 'border-accent bg-accent/[0.08] shadow-lg shadow-accent/10'
                      : 'border-border-default bg-bg-elevated hover:border-accent/40 hover:bg-bg-elevated/80'
                  }`}
                >
                  <div className="min-w-0">
                    <div className={`text-xs sm:text-sm font-semibold truncate ${active ? 'text-fg' : 'text-fg-muted'}`}>
                      {label}
                    </div>
                    <div className="mt-0.5 text-[10px] font-mono uppercase tracking-[0.18em] text-fg-subtle">
                      {sub}
                    </div>
                  </div>
                  <span className={`text-[11px] font-mono font-bold tracking-wider shrink-0 ${
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

  const [phase, setPhase] = useState('landing');

  useEffect(() => {
    if (phase !== 'landing') return undefined;
    const handle = window.setTimeout(() => setPhase('preferences'), 1900);
    return () => window.clearTimeout(handle);
  }, [phase]);

  const show = !isLoading && preferences && preferences.userSub && preferences.onboardingCompleted === false;

  const closingRef = useRef(false);
  const farewellStartedRef = useRef(false);

  // Single farewell for every exit path (tour finish, skip, escape, mobile, preferences-skip):
  // fade in → hold → advance to 'closing' so AnimatePresence plays the exit fade. The
  // onboardingCompleted mutation is deferred to onExitComplete so its optimistic cache write
  // can't flip `show` and unmount the gate mid fade-out (which made skip/esc cut abruptly).
  const showFarewell = useCallback(() => {
    if (farewellStartedRef.current) return;
    farewellStartedRef.current = true;
    setPhase('farewell');
    window.setTimeout(() => {
      closingRef.current = true;
      setPhase('closing');
    }, 2600);
  }, []);

  const handleFarewellExitComplete = useCallback(() => {
    if (!closingRef.current) return;
    closingRef.current = false;
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

  const isMobileDevice = useMemo(() => {
    if (typeof navigator === 'undefined') return false;
    return /Mobi|Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
  }, []);

  const handleStartTour = useCallback(() => {
    if (isMobileDevice) {
      showFarewell();
      return;
    }
    setPhase('tour');
  }, [isMobileDevice, showFarewell]);

  const handleSkipToFarewell = useCallback(() => {
    showFarewell();
  }, [showFarewell]);

  useEffect(() => {
    if (!show) return undefined;
    if (phase !== 'preferences') return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        handleSkipToFarewell();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [show, phase, handleSkipToFarewell]);

  if (!show) return null;

  return (
    <>
      <AnimatePresence onExitComplete={handleFarewellExitComplete}>
        {phase === 'landing' && (
          <motion.div
            key="landing"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, transition: { duration: 0.35, ease: [0.4, 0, 0.4, 1] } }}
            transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
            className="fixed inset-0 z-[80] flex items-center justify-center bg-bg-base/95"
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.9, y: 16 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: -8, transition: { duration: 0.3, ease: [0.4, 0, 0.4, 1] } }}
              transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
              className="flex flex-col items-center gap-4"
            >
              <motion.span
                aria-hidden="true"
                className="flex items-center justify-center w-16 h-16 rounded-2xl logo-gradient text-white shadow-2xl shadow-accent/30"
                animate={{ y: [0, -3, 0] }}
                transition={{ duration: 2.2, repeat: Infinity, ease: 'easeInOut' }}
              >
                <TrendingUp className="h-7 w-7" />
              </motion.span>
              <motion.h1
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.25, duration: 0.55, ease: [0.22, 1, 0.36, 1] }}
                className="font-display text-2xl sm:text-3xl font-bold tracking-tight text-center bg-gradient-to-r from-accent via-accent-bright to-accent bg-clip-text text-transparent"
                style={{ backgroundSize: '200% 100%' }}
              >
                {t('onboarding.landing.title', { defaultValue: 'Finance Portal' })}
              </motion.h1>
              <motion.p
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.55, duration: 0.5 }}
                className="text-sm text-zinc-800 dark:text-zinc-200 text-center max-w-xs font-medium"
              >
                {t('onboarding.landing.subtitle', { defaultValue: 'Hoş geldin — birkaç ayar sonra hazırsın.' })}
              </motion.p>
              <motion.div
                aria-hidden="true"
                className="mt-2 h-[2px] w-24 rounded-full bg-gradient-to-r from-transparent via-accent to-transparent"
                initial={{ scaleX: 0, opacity: 0 }}
                animate={{ scaleX: 1, opacity: 1 }}
                transition={{ delay: 0.85, duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
              />
            </motion.div>
          </motion.div>
        )}
        {phase === 'preferences' && (
          <div className="fixed inset-0 z-[80] flex items-center justify-center p-3 sm:p-4 overflow-hidden">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="absolute inset-0 bg-bg-base/95"
            />

            <motion.div
              initial={{ opacity: 0, scale: 0.96, y: 12 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.96, y: 8 }}
              transition={{ type: 'spring', stiffness: 260, damping: 28 }}
              style={{ width: 'min(560px, calc(100vw - 1.5rem))' }}
              className="relative max-h-[calc(100vh-1.5rem)] sm:max-h-[calc(100vh-2rem)] overflow-y-auto overflow-x-hidden rounded-2xl sm:rounded-3xl border border-border-default bg-bg-elevated backdrop-blur-md shadow-2xl shadow-black/20"
            >
              <button
                type="button"
                onClick={handleSkipToFarewell}
                disabled={updatePreferences.isPending}
                aria-label={t('onboarding.skip')}
                className="absolute top-3 right-3 sm:top-4 sm:right-4 z-20 inline-flex items-center justify-center gap-1.5 rounded-lg border border-border-default bg-bg-base/80 min-w-[40px] min-h-[40px] sm:min-w-0 sm:min-h-0 sm:px-2.5 sm:py-1.5 text-[11px] text-fg-muted backdrop-blur-md transition-colors hover:text-fg hover:border-accent/40 cursor-pointer disabled:opacity-50"
              >
                <X className="h-4 w-4 sm:h-3 sm:w-3" />
                <span className="hidden sm:inline">{t('onboarding.skip')}</span>
              </button>
              <span aria-hidden="true" className="pointer-events-none absolute -top-32 left-1/2 -translate-x-1/2 w-[420px] h-56 rounded-full bg-accent/[0.10] blur-[110px]" />
              <span aria-hidden="true" className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/60 to-transparent" />

              <div className="relative px-5 pt-8 pb-2 sm:px-10 sm:pt-9">
                <div className="text-center pr-12 sm:pr-0">
                  <div className="text-[11px] text-fg-subtle">
                    {t('onboarding.tour.phaseLabel')}
                  </div>
                </div>
              </div>

              <div className="relative px-5 pb-6 sm:px-10 sm:pb-8">
                <PreferencesStep
                  themePreference={themePreference}
                  setThemePreference={handleThemeChange}
                  language={i18nInstance.language}
                  onLanguageChange={handleLanguageChange}
                  t={t}
                />
              </div>

              <div className="relative flex items-center justify-end gap-3 border-t border-border-default px-5 py-4 sm:px-10 bg-bg-base/40">
                <motion.button
                  type="button"
                  onClick={handleStartTour}
                  whileHover={{ x: 3, y: -2 }}
                  whileTap={{ scale: 0.97 }}
                  className="inline-flex items-center justify-center gap-2 rounded-xl bg-gradient-accent px-4 sm:px-5 py-2.5 min-h-[44px] text-sm font-semibold text-white shadow-lg shadow-accent/30 transition-all duration-200 border-none cursor-pointer"
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
        {phase === 'farewell' && (
          <motion.div
            key="farewell"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, transition: { duration: 1.0, ease: [0.4, 0, 0.4, 1] } }}
            transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
            className="fixed inset-0 z-[80] flex items-center justify-center"
            style={{ backgroundColor: 'rgb(2, 6, 23)' }}
            aria-hidden="true"
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.85, y: 18 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 1.02, y: -6, transition: { duration: 0.7, ease: [0.4, 0, 0.4, 1] } }}
              transition={{ duration: 0.9, ease: [0.22, 1, 0.36, 1] }}
              className="relative flex flex-col items-center gap-3 px-6"
            >
              <motion.span
                aria-hidden="true"
                className="absolute -top-7 text-2xl"
                animate={{ scale: [0, 1.2, 0.9, 1.2, 0], rotate: [0, 180, 360], opacity: [0, 1, 1, 1, 0] }}
                transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
              >
                ✨
              </motion.span>
              <motion.h2
                style={{
                  color: '#f4f4f5',
                  backgroundImage: 'linear-gradient(to right, #67e8f9, #e879f9, #fcd34d)',
                  backgroundSize: '200% 100%',
                  WebkitBackgroundClip: 'text',
                  backgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                }}
                className="font-display text-3xl sm:text-5xl font-bold tracking-tight text-center drop-shadow-[0_0_24px_rgba(168,85,247,0.45)]"
                animate={{ backgroundPosition: ['0% 50%', '200% 50%'] }}
                transition={{ duration: 3.2, repeat: Infinity, ease: 'linear' }}
              >
                {t('onboarding.tour.farewell.title', { defaultValue: 'Hoş geldin' })}
              </motion.h2>
              <motion.p
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.4, duration: 0.6 }}
                style={{ color: '#e4e4e7' }}
                className="text-sm sm:text-base text-center max-w-xs leading-relaxed font-medium px-4"
              >
                {t('onboarding.tour.farewell.subtitle', {
                  defaultValue: 'Keyifli takipler dileriz, hep yanındayız.',
                })}
              </motion.p>
              <motion.div
                aria-hidden="true"
                className="mt-2 h-[2px] w-24 rounded-full bg-gradient-to-r from-transparent via-accent to-transparent"
                initial={{ scaleX: 0, opacity: 0 }}
                animate={{ scaleX: 1, opacity: 1 }}
                transition={{ delay: 0.9, duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
              />
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <ProductTour
        open={phase === 'tour'}
        onFinish={showFarewell}
        onSkip={showFarewell}
      />
    </>
  );
}
