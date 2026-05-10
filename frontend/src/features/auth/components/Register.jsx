import React from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useAuth } from '../AuthContext';
import { useTheme } from '../../../shared/context/ThemeContext';
import { useNavigate } from 'react-router-dom';
import { Shield, Target, Lock, UserPlus, Sun, Moon } from 'lucide-react';
import i18n from '../../../shared/i18n/config';

const FEATURE_ITEMS = [
  { icon: Shield, key: 'secureAuth' },
  { icon: Target, key: 'rbac' },
  { icon: Lock, key: 'twoFactor' },
];

const LANGS = ['tr', 'en'];

const Register = () => {
  const { t, i18n: i18nInstance } = useTranslation();
  const { isAuthenticated, login } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const activeLang = (i18nInstance.language || 'en').slice(0, 2);

  React.useEffect(() => {
    if (isAuthenticated) {
      navigate('/market');
    }
  }, [isAuthenticated, navigate]);

  const handleRegister = () => {
    login({ redirectUri: window.location.origin, action: 'register' });
  };
  const handleLangChange = (lang) => {
    if (lang !== activeLang) i18n.changeLanguage(lang);
  };

  if (isAuthenticated) {
    return null;
  }
  return (
    <div className="flex justify-center items-center min-h-screen p-6">
      <div className="fixed top-5 right-5 z-50 flex items-center gap-2">
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
        <button
          onClick={toggleTheme}
          className="flex items-center justify-center w-9 h-9 rounded-lg border border-border-default bg-bg-elevated backdrop-blur-md text-fg-muted hover:text-fg hover:bg-surface transition-all cursor-pointer"
        >
          {isDark ? <Sun size={15} /> : <Moon size={15} />}
        </button>
      </div>
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        className="relative rounded-2xl border border-border-default bg-bg-elevated card-hover backdrop-blur-md max-w-[460px] w-full overflow-hidden"
      >
        {isDark && (
          <span className="pointer-events-none absolute -top-20 left-1/2 -translate-x-1/2 w-60 h-40 rounded-full bg-accent/[0.07] blur-[80px]" aria-hidden="true" />
        )}
        <div className="relative px-6 py-6 border-b border-border-default">
          <h1 className="text-xl font-display text-fg">Finance Portal</h1>
          <p className="text-fg-muted text-sm mt-1">{t('register.subtitle')}</p>
        </div>
        <div className="relative px-6 py-6">
          <p className="text-fg-muted text-sm mb-6 leading-relaxed">
            {t('register.intro')}
          </p>
          <button
            onClick={handleRegister}
            className="w-full flex items-center justify-center gap-2 py-3 px-5 text-sm font-semibold text-white bg-gradient-accent rounded-xl cursor-pointer transition-all duration-200 hover:-translate-y-0.5 active:scale-[0.98] mb-6"
            style={{
              boxShadow: isDark
                ? '0 4px 14px rgba(99,102,241,0.3)'
                : '0 4px 14px rgba(0,82,255,0.25)',
            }}
          >
            <UserPlus size={16} strokeWidth={1.8} />
            {t('register.cta')}
          </button>
          <div className="flex flex-col gap-2">
            {FEATURE_ITEMS.map(({ icon: Icon, key }) => (
              <div
                key={key}
                className="group flex items-center gap-3 px-3 py-2.5 rounded-lg border border-border-default bg-bg-base transition-all duration-150 hover:bg-surface hover:border-border-hover"
              >
                <span className="flex items-center justify-center w-7 h-7 rounded-md bg-accent/10 text-accent group-hover:bg-accent/20 transition-colors duration-150 shrink-0">
                  <Icon size={14} strokeWidth={1.8} />
                </span>
                <div>
                  <h4 className="text-fg text-sm font-medium">{t(`register.features.${key}.title`)}</h4>
                  <p className="text-fg-muted text-xs">{t(`register.features.${key}.desc`)}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
        <div className="px-6 py-4 border-t border-border-default">
          <p className="text-fg-subtle text-xs">
            {t('register.haveAccount')}{' '}
            <a
              href="/login"
              className="text-accent hover:text-accent-bright transition-colors duration-150 no-underline font-medium"
            >
              {t('register.loginHere')}
            </a>
          </p>
        </div>
      </motion.div>
    </div>
  );
};
export default Register;
