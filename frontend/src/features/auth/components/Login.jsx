import React from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import Card from '../../../shared/components/card';
import { useAuth } from '../AuthContext';
import { useTheme } from '../../../shared/context/ThemeContext';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, KeyRound, Lock, Globe, Rocket } from 'lucide-react';
import i18n from '../../../shared/i18n/config';

const LANGS = ['tr', 'en'];

const FEATURE_ITEMS = [
  { icon: ShieldCheck, key: 'secureAuth' },
  { icon: KeyRound, key: 'rbac' },
  { icon: Lock, key: 'twoFactor' },
  { icon: Globe, key: 'sso' },
];

const Login = () => {
  const { t, i18n: i18nInstance } = useTranslation();
  const { isAuthenticated, login } = useAuth();
  const { isDark } = useTheme();
  const navigate = useNavigate();
  const activeLang = (i18nInstance.language || 'en').slice(0, 2);

  const handleLangChange = (lang) => {
    if (lang !== activeLang) i18n.changeLanguage(lang);
  };

  React.useEffect(() => {
    if (isAuthenticated) {
      navigate('/market');
    }
  }, [isAuthenticated, navigate]);

  if (isAuthenticated) {
    return null;
  }

  return (
    <div className="flex justify-center items-center min-h-screen p-6">
      <Card
        as={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        variant="elevated"
        radius="2xl"
        padding="none"
        backdropBlur
        className="max-w-[460px] w-full"
      >
        {isDark && (
          <span className="pointer-events-none absolute -top-20 left-1/2 -translate-x-1/2 w-60 h-40 rounded-full bg-accent/[0.07] blur-[80px]" aria-hidden="true" />
        )}

        <div className="relative px-6 py-6 border-b border-border-default flex items-start justify-between gap-3">
          <div>
            <h1 className="text-xl font-display text-fg">Finance Portal</h1>
            <p className="text-fg-muted text-sm mt-1">{t('login.welcome')}</p>
          </div>
          <div className="inline-flex items-center gap-0.5 rounded-lg border border-border-default bg-bg-base p-0.5 shrink-0">
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
        </div>

        <div className="relative px-6 py-6">
          <div className="mb-6">
            <h3 className="flex items-center gap-2 text-fg text-base font-medium mb-1">
              <span className="flex items-center justify-center w-7 h-7 rounded-md bg-accent/10 text-accent">
                <Lock size={14} strokeWidth={1.8} />
              </span>
              {t('login.required')}
            </h3>
            <p className="text-fg-muted text-sm ml-9">{t('login.intro')}</p>
            <div className="flex flex-col gap-2 mt-4">
              {FEATURE_ITEMS.map(({ icon: Icon, key }) => (
                <div
                  key={key}
                  className="group flex items-center gap-3 px-3 py-2.5 rounded-lg border border-border-default bg-bg-base transition-all duration-150 hover:bg-surface hover:border-border-hover"
                >
                  <Icon size={16} strokeWidth={1.8} className="text-fg-subtle group-hover:text-accent transition-colors duration-150 shrink-0" />
                  <span className="text-fg-muted text-sm font-medium">{t(`login.features.${key}`)}</span>
                </div>
              ))}
            </div>
          </div>

          <button
            onClick={login}
            className="w-full flex items-center justify-center gap-2 py-3 px-5 text-sm font-semibold text-white bg-gradient-accent rounded-xl border-none cursor-pointer transition-all duration-200 hover:-translate-y-0.5 active:scale-[0.98]"
            style={{
              boxShadow: isDark
                ? '0 4px 14px rgba(99,102,241,0.3)'
                : '0 4px 14px rgba(0,82,255,0.25)',
            }}
          >
            <Rocket size={16} strokeWidth={1.8} />
            {t('login.cta')}
          </button>

        </div>

        <div className="px-6 py-4 border-t border-border-default">
          <p className="text-fg-subtle text-xs">{t('login.poweredBy')}</p>
        </div>
      </Card>
    </div>
  );
};

export default Login;
