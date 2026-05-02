import React from 'react';
import { useAuth } from './AuthContext';
import { useTheme } from '../../shared/context/ThemeContext';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ShieldCheck, KeyRound, Lock, Globe, Rocket } from 'lucide-react';

const featureItems = [
  { icon: ShieldCheck, label: 'Secure Authentication' },
  { icon: KeyRound, label: 'Role-Based Access Control' },
  { icon: Lock, label: 'Two-Factor Authentication (2FA)' },
  { icon: Globe, label: 'Single Sign-On (SSO)' },
];

const Login = () => {
  const { isAuthenticated, login } = useAuth();
  const { isDark } = useTheme();
  const navigate = useNavigate();

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
          <p className="text-fg-muted text-sm mt-1">Hesabınıza giriş yapın</p>
        </div>

        <div className="relative px-6 py-6">
          <div className="mb-6">
            <h3 className="flex items-center gap-2 text-fg text-base font-medium mb-1">
              <span className="flex items-center justify-center w-7 h-7 rounded-md bg-accent/10 text-accent">
                <Lock size={14} strokeWidth={1.8} />
              </span>
              Giriş Gerekli
            </h3>
            <p className="text-fg-muted text-sm ml-9">Finance Portal'a erişmek için giriş yapın.</p>
            <div className="flex flex-col gap-2 mt-4">
              {featureItems.map((item, i) => {
                const Icon = item.icon;
                return (
                  <div
                    key={i}
                    className="group flex items-center gap-3 px-3 py-2.5 rounded-lg border border-border-default bg-bg-base transition-all duration-150 hover:bg-surface hover:border-border-hover"
                  >
                    <Icon size={16} strokeWidth={1.8} className="text-fg-subtle group-hover:text-accent transition-colors duration-150 shrink-0" />
                    <span className="text-fg-muted text-sm font-medium">{item.label}</span>
                  </div>
                );
              })}
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
            Keycloak ile Giriş Yap
          </button>

        </div>

        <div className="px-6 py-4 border-t border-border-default">
          <p className="text-fg-subtle text-xs">Powered by Keycloak & Spring Security</p>
        </div>
      </motion.div>
    </div>
  );
};

export default Login;
