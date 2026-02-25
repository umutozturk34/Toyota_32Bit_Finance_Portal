import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ShieldCheck, KeyRound, Lock, Globe, Rocket } from 'lucide-react';
const featureItems = [
  { icon: ShieldCheck, label: 'Secure JWT Authentication' },
  { icon: KeyRound, label: 'Role-Based Access Control' },
  { icon: Lock, label: 'Two-Factor Authentication (2FA)' },
  { icon: Globe, label: 'Single Sign-On (SSO)' },
];
const Login = () => {
  const { isAuthenticated, user, login } = useAuth();
  const { isDark } = useTheme();
  const navigate = useNavigate();
  React.useEffect(() => {
    if (isAuthenticated) {
      navigate('/users');
    }
  }, [isAuthenticated, navigate]);
  if (isAuthenticated) {
    return null;
  }
  return (
    <div className="flex justify-center items-center min-h-[calc(100vh-120px)] p-6">
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        className="relative rounded-xl border border-border-default bg-bg-elevated max-w-[460px] w-full overflow-hidden card-hover"
      >
        {}
        {isDark && (
          <span className="pointer-events-none absolute -top-20 left-1/2 -translate-x-1/2 w-60 h-40 rounded-full bg-accent/[0.07] blur-[80px]" aria-hidden="true" />
        )}
        {}
        <div className="relative px-6 py-6 border-b border-border-default">
          <h1 className="text-xl font-bold text-fg">Finance Portal</h1>
          <p className="text-fg-muted text-sm mt-1">Secure Authentication with Keycloak</p>
        </div>
        {}
        <div className="relative px-6 py-6">
          <div className="mb-6">
            <h3 className="flex items-center gap-2 text-fg text-base font-medium mb-1">
              <span className="flex items-center justify-center w-7 h-7 rounded-md bg-accent/10 text-accent">
                <Lock size={14} strokeWidth={1.8} />
              </span>
              Authentication Required
            </h3>
            <p className="text-fg-muted text-sm ml-9">Please login to access the Finance Portal.</p>
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
          {}
          <button
            onClick={login}
            className="w-full flex items-center justify-center gap-2 py-2.5 px-5 text-sm font-semibold text-white bg-accent rounded-lg cursor-pointer transition-all duration-150 hover:bg-accent-bright active:scale-[0.98]"
            style={{
              boxShadow: isDark
                ? '0 0 0 1px rgba(94,106,210,0.5), 0 2px 12px rgba(94,106,210,0.25), inset 0 1px 0 0 rgba(255,255,255,0.1)'
                : undefined,
            }}
          >
            <Rocket size={16} strokeWidth={1.8} />
            Login with Keycloak
          </button>
          {}
          <div className="mt-6 p-4 rounded-lg border border-border-default bg-bg-base">
            <h4 className="text-fg text-sm font-medium mb-2">Demo Accounts</h4>
            <p className="text-fg-muted text-xs mb-3 leading-relaxed">
              Default credentials are configured via environment variables.
              Check .env.example for setup instructions.
            </p>
            <div className="flex items-center gap-2 py-1.5 text-fg-muted text-sm flex-wrap">
              <strong className="text-fg">Admin Role:</strong> admin / [see .env]
              <span className="ml-auto inline-block px-2 py-0.5 bg-accent/15 text-accent-bright rounded text-xs font-medium">
                ADMIN + USER
              </span>
            </div>
            <div className="flex items-center gap-2 py-1.5 text-fg-muted text-sm flex-wrap">
              <strong className="text-fg">User Role:</strong> user / [see .env]
              <span className="ml-auto inline-block px-2 py-0.5 bg-accent/15 text-accent-bright rounded text-xs font-medium">
                USER
              </span>
            </div>
          </div>
        </div>
        {}
        <div className="px-6 py-4 border-t border-border-default">
          <p className="text-fg-subtle text-xs">Powered by Keycloak & Spring Security</p>
        </div>
      </motion.div>
    </div>
  );
};
export default Login;
