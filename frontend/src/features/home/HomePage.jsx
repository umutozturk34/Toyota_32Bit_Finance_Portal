import { useMemo, useEffect } from 'react';
import { STALE } from '../../shared/constants/query';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import { useTheme } from '../../shared/context/useTheme';
import Card from '../../shared/components/card';
import { useAuth } from '../auth/useAuth';
import { unifiedMarketService } from '../../shared/services/unifiedMarketService';
import i18n from '../../shared/i18n/config';
import {
  UserPlus, LogIn, ArrowRight, Sun, Moon,
} from 'lucide-react';

import { HeroGraphic, AnimatedDotGrid } from './components/HeroGraphic';
import { easeOut, FEATURE_DEFS, GLOW_POSITIONS, STAT_DEFS, buildFloatingCards } from './lib/homePageConstants';

const LANGS = ['tr', 'en'];

const HomePage = () => {
  const { t, i18n: i18nInstance } = useTranslation();
  const { isDark, toggleTheme } = useTheme();
  const { isAuthenticated, login } = useAuth();

  useEffect(() => {
    document.documentElement.dataset.themeFade = '1';
    return () => { delete document.documentElement.dataset.themeFade; };
  }, []);

  const handleLogin = () => login();
  const handleRegister = () => login({ action: 'register' });
  const activeLang = (i18nInstance.language || 'en').slice(0, 2);
  const handleLangChange = (lang) => {
    if (lang !== activeLang) i18n.changeLanguage(lang);
  };

  const { data: overview } = useQuery({
    queryKey: ['marketOverview'],
    queryFn: () => unifiedMarketService.getOverview(),
    enabled: isAuthenticated,
    staleTime: STALE.MEDIUM,
  });

  const floatingCards = useMemo(() => buildFloatingCards(overview), [overview]);

  return (
    <div className="min-h-screen min-h-[100dvh] bg-bg-base relative overflow-hidden">
      <div className="fixed top-[max(1.25rem,env(safe-area-inset-top))] right-[max(1.25rem,env(safe-area-inset-right))] z-50 flex items-center gap-2">
        <div className="inline-flex items-center gap-0.5 rounded-lg border border-border-default bg-bg-elevated backdrop-blur-md p-0.5">
          {LANGS.map((lang) => {
            const active = lang === activeLang;
            return (
              <button
                key={lang}
                type="button"
                onClick={() => handleLangChange(lang)}
                className={`px-2 py-1 rounded-md text-[10px] font-mono font-bold uppercase tracking-[0.16em] transition-colors duration-150 cursor-pointer border-none ${
                  active
                    ? 'bg-accent/15 text-accent'
                    : 'bg-transparent text-fg-subtle hover:text-fg'
                }`}
              >
                {lang}
              </button>
            );
          })}
        </div>
        <motion.button
          onClick={toggleTheme}
          whileTap={{ scale: 0.9 }}
          aria-label={isDark ? t('home.toggleThemeLight', { defaultValue: 'Switch to light mode' }) : t('home.toggleThemeDark', { defaultValue: 'Switch to dark mode' })}
          className="relative overflow-hidden flex items-center justify-center w-9 h-9 rounded-lg border border-border-default bg-bg-elevated backdrop-blur-md text-fg-muted hover:text-fg hover:bg-surface transition-colors cursor-pointer"
        >
          <span
            aria-hidden="true"
            className={`pointer-events-none absolute inset-0 rounded-lg transition-opacity duration-500 ${isDark ? 'opacity-0' : 'opacity-100'}`}
            style={{ background: 'radial-gradient(circle at center, rgba(251,191,36,0.18), transparent 65%)' }}
          />
          <span
            aria-hidden="true"
            className={`pointer-events-none absolute inset-0 rounded-lg transition-opacity duration-500 ${isDark ? 'opacity-100' : 'opacity-0'}`}
            style={{ background: 'radial-gradient(circle at center, rgba(129,140,248,0.18), transparent 65%)' }}
          />
          <AnimatePresence mode="wait" initial={false}>
            <motion.span
              key={isDark ? 'sun' : 'moon'}
              initial={{ rotate: -120, scale: 0.4, opacity: 0 }}
              animate={{ rotate: 0, scale: 1, opacity: 1 }}
              exit={{ rotate: 120, scale: 0.4, opacity: 0 }}
              transition={{ duration: 0.28, ease: [0.4, 0, 0.2, 1] }}
              className="relative inline-flex"
            >
              {isDark ? <Sun size={15} /> : <Moon size={15} />}
            </motion.span>
          </AnimatePresence>
        </motion.button>
      </div>

      {isDark && (
        <div aria-hidden="true" className="pointer-events-none absolute inset-0" style={{ isolation: 'isolate', transform: 'translate3d(0,0,0)' }}>
          <div
            className="pointer-events-none absolute top-[-120px] left-[20%] w-[600px] h-[500px] rounded-full blur-[180px]"
            style={{ background: 'radial-gradient(circle, rgba(99,102,241,0.1) 0%, transparent 70%)', willChange: 'transform', transform: 'translate3d(0,0,0)' }}
          />
          <div
            className="pointer-events-none absolute bottom-[-80px] right-[15%] w-[400px] h-[350px] rounded-full blur-[140px]"
            style={{ background: 'radial-gradient(circle, rgba(124,58,237,0.07) 0%, transparent 70%)', willChange: 'transform', transform: 'translate3d(0,0,0)' }}
          />
        </div>
      )}
      {!isDark && (
        <div aria-hidden="true" className="pointer-events-none absolute inset-0" style={{ isolation: 'isolate', transform: 'translate3d(0,0,0)' }}>
          <div
            className="pointer-events-none absolute top-[-80px] right-[20%] w-[500px] h-[450px] rounded-full blur-[160px]"
            style={{ background: 'radial-gradient(circle, rgba(0,82,255,0.06) 0%, transparent 70%)', willChange: 'transform', transform: 'translate3d(0,0,0)' }}
          />
          <div
            className="pointer-events-none absolute bottom-[20%] left-[10%] w-[300px] h-[300px] rounded-full blur-[120px]"
            style={{ background: 'radial-gradient(circle, rgba(0,82,255,0.04) 0%, transparent 70%)', willChange: 'transform', transform: 'translate3d(0,0,0)' }}
          />
        </div>
      )}

      <div className="max-w-6xl mx-auto px-6 sm:px-8">
        <section className="relative pt-16 pb-24 md:pt-24 md:pb-32">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 lg:gap-12 items-center">
            <div className="space-y-8 text-center lg:text-left">
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.05, ease: easeOut }}
                className="flex justify-center lg:justify-start"
              >
                <span className="inline-flex items-center gap-2.5 px-4 py-2 rounded-full text-xs font-mono uppercase tracking-[0.12em] border border-border-accent bg-accent-glow text-accent">
                  <span className="relative flex h-2 w-2">
                    <span className="animate-pulse-dot absolute inline-flex h-full w-full rounded-full bg-accent opacity-75" />
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-accent" />
                  </span>
                  {t('home.live')}
                </span>
              </motion.div>

              <motion.h1
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, delay: 0.12, ease: easeOut }}
                className="text-[2.5rem] md:text-[3.25rem] lg:text-[3.75rem] font-display leading-[1.08] tracking-[-0.02em] text-fg"
              >
                {t('home.heroTitle.part1')}{' '}
                <span className="relative inline-block">
                  <span className="text-gradient">{t('home.heroTitle.part2')}</span>
                  <span className="absolute bottom-[-0.15rem] md:bottom-[-0.3rem] left-0 h-2 md:h-2.5 w-full rounded-sm bg-gradient-to-r from-accent/15 to-accent-bright/10" />
                </span>
              </motion.h1>

              <motion.p
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.22, ease: easeOut }}
                className="text-base md:text-lg text-fg-muted max-w-lg leading-relaxed mx-auto lg:mx-0"
              >
                {t('home.heroDescription')}
              </motion.p>

              <motion.div
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.3, ease: easeOut }}
                className="flex flex-wrap items-center gap-3 justify-center lg:justify-start"
              >
                <button
                  onClick={handleRegister}
                  className="group relative flex items-center gap-2 px-7 py-3 text-sm font-semibold border-none rounded-xl cursor-pointer bg-gradient-accent text-white transition-all duration-200 hover:-translate-y-0.5 active:scale-[0.98]"
                  style={{
                    boxShadow: isDark
                      ? '0 4px 20px rgba(99,102,241,0.35)'
                      : '0 4px 20px rgba(0,82,255,0.3)',
                  }}
                >
                  <UserPlus size={15} strokeWidth={1.6} />
                  {t('home.cta.createAccount')}
                  <ArrowRight size={14} strokeWidth={2} className="ml-0.5 opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-200" />
                </button>
                <button
                  onClick={handleLogin}
                  className="group flex items-center gap-2 px-6 py-3 text-sm font-semibold rounded-xl cursor-pointer bg-transparent text-fg border border-border-default hover:bg-surface hover:border-border-hover transition-all duration-200 hover:-translate-y-0.5"
                >
                  <LogIn size={15} strokeWidth={1.6} />
                  {t('home.cta.signIn')}
                </button>
              </motion.div>

              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.6, delay: 0.5 }}
                className="flex items-center gap-5 sm:gap-6 pt-2 justify-center lg:justify-start flex-wrap"
              >
                {STAT_DEFS.map((s) => {
                  const Icon = s.icon;
                  return (
                    <div key={s.key} className="flex items-center gap-1.5">
                      <Icon size={13} className="text-accent" />
                      <span className="text-base font-bold text-fg tracking-tight">{s.value}</span>
                      <span className="text-[10px] text-fg-subtle uppercase tracking-wider">{t(`home.stats.${s.key}`)}</span>
                    </div>
                  );
                })}
              </motion.div>
            </div>

            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.8, delay: 0.2, ease: easeOut }}
              className="hidden lg:block"
            >
              <HeroGraphic isDark={isDark} cards={floatingCards} />
            </motion.div>
          </div>
        </section>

        <div className="section-line" />

        <section className="py-20 md:py-28">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: '-60px' }}
            transition={{ duration: 0.7, ease: easeOut }}
            className="text-center mb-14"
          >
            <span className="inline-flex items-center gap-2.5 px-4 py-1.5 rounded-full text-xs font-mono uppercase tracking-[0.12em] border border-border-accent bg-accent-glow text-accent mb-6">
              <span className="h-1.5 w-1.5 rounded-full bg-accent" />
              {t('home.featuresTag')}
            </span>
            <h2 className="text-3xl md:text-[2.5rem] font-display tracking-normal text-fg">
              {t('home.featuresHeading.part1')}{' '}
              <span className="text-fg-muted">{t('home.featuresHeading.part2')}</span>
            </h2>
          </motion.div>

          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, margin: '-40px' }}
            variants={{ hidden: {}, visible: { transition: { staggerChildren: 0.1 } } }}
            className="grid grid-cols-1 md:grid-cols-2 gap-4"
          >
            {FEATURE_DEFS.map((f) => {
              const Icon = f.icon;
              return (
                <Card
                  as={motion.div}
                  key={f.key}
                  variants={{
                    hidden: { opacity: 0, y: 28 },
                    visible: { opacity: 1, y: 0, transition: { duration: 0.7, ease: easeOut } },
                  }}
                  variant="elevated"
                  radius="2xl"
                  padding="xl"
                  backdropBlur
                  className="group"
                  style={{
                    borderColor: isDark ? 'rgba(99,102,241,0.15)' : 'rgba(0,82,255,0.12)',
                  }}
                >
                  <div
                    className="pointer-events-none absolute w-[200px] h-[200px] rounded-full blur-[80px] opacity-80 group-hover:opacity-100 transition-opacity duration-500"
                    style={{
                      ...GLOW_POSITIONS[f.glowPos],
                      background: `radial-gradient(circle, ${isDark ? f.glowColor.dark : f.glowColor.light} 0%, transparent 70%)`,
                      willChange: 'opacity',
                      transform: 'translate3d(0,0,0)',
                    }}
                    aria-hidden="true"
                  />
                  <span className="relative inline-flex items-center justify-center w-11 h-11 rounded-xl bg-gradient-accent text-white mb-5 shadow-lg shadow-accent/20 transition-transform duration-300 group-hover:scale-110">
                    <Icon size={20} strokeWidth={1.5} />
                  </span>
                  <h3 className="relative text-lg font-semibold text-fg mb-2">{t(`home.features.${f.key}.title`)}</h3>
                  <p className="relative text-sm text-fg-muted leading-relaxed">{t(`home.features.${f.key}.description`)}</p>
                </Card>
              );
            })}
          </motion.div>
        </section>

        <section className="relative py-20 md:py-28 -mx-6 sm:-mx-8 px-6 sm:px-8 rounded-3xl overflow-hidden" style={{ background: isDark ? '#0F172A' : 'var(--color-fg)' }}>
          <AnimatedDotGrid isDark={!isDark} />

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: '-60px' }}
            transition={{ duration: 0.7 }}
            className="relative z-10 flex flex-col items-center text-center mt-8"
          >
            <span className="inline-flex items-center gap-2.5 px-4 py-1.5 rounded-full text-xs font-mono uppercase tracking-[0.12em] border border-white/10 bg-white/5 text-white/80 mb-6">
              <span className="h-1.5 w-1.5 rounded-full bg-white/60 animate-pulse-dot" />
              {t('home.getStartedTag')}
            </span>

            <h2 className="text-3xl md:text-4xl font-display text-white mb-4">
              {t('home.ctaSection.heading')}
            </h2>
            <p className="text-white/60 mb-10 max-w-md mx-auto text-base leading-relaxed">
              {t('home.ctaSection.body')}
            </p>

            <div className="flex flex-wrap items-center gap-3 justify-center">
              <button
                onClick={handleRegister}
                className="group relative inline-flex items-center gap-2 px-8 py-3.5 text-sm font-semibold border-none rounded-xl cursor-pointer bg-white text-[#0F172A] transition-all duration-200 hover:-translate-y-0.5 hover:shadow-xl active:scale-[0.98]"
              >
                <UserPlus size={15} strokeWidth={1.6} />
                {t('home.cta.createAccount')}
                <ArrowRight size={15} strokeWidth={2} className="opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-200" />
              </button>
              <button
                onClick={handleLogin}
                className="group inline-flex items-center gap-2 px-6 py-3.5 text-sm font-semibold rounded-xl cursor-pointer bg-transparent text-white/80 border border-white/15 hover:bg-white/5 hover:border-white/25 hover:text-white transition-all duration-200 hover:-translate-y-0.5"
              >
                <LogIn size={15} strokeWidth={1.6} />
                {t('home.cta.signIn')}
              </button>
            </div>
          </motion.div>

          <AnimatedDotGrid isDark={!isDark} />
        </section>

        <footer className="py-8 text-center">
          <p className="text-xs text-fg-subtle">{t('home.footer')}</p>
        </footer>
      </div>
    </div>
  );
};

export default HomePage;
