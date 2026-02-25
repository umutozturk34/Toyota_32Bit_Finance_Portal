import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { motion } from 'framer-motion';
import {
  Shield, BarChart3, Briefcase, LogIn, UserPlus,
  TrendingUp, Globe, Zap, LineChart, ArrowRight
} from 'lucide-react';
const features = [
  {
    icon: Shield,
    title: 'Bank-Grade Security',
    description: 'Keycloak SSO, LDAP federation, 2FA/TOTP — your credentials never touch our servers.',
    span: 'col-span-1 md:col-span-2',      
  },
  {
    icon: BarChart3,
    title: 'Live Market Data',
    description: 'Real-time quotes across stocks, crypto, forex & metals with sub-second latency.',
    span: 'col-span-1',
  },
  {
    icon: LineChart,
    title: 'Advanced Charting',
    description: 'TradingView-powered charts with EMA, Bollinger, Fibonacci, custom drawing tools & magnet snap.',
    span: 'col-span-1',
  },
  {
    icon: Briefcase,
    title: 'Portfolio Tracking',
    description: 'Build watchlists, track P&L, and monitor positions across every asset class.',
    span: 'col-span-1 md:col-span-2',      
  },
];
const containerV = {
  hidden: {},
  show: { transition: { staggerChildren: 0.07, delayChildren: 0.25 } },
};
const itemV = {
  hidden: { opacity: 0, y: 14 },
  show:   { opacity: 1, y: 0, transition: { duration: 0.45, ease: [0.16, 1, 0.3, 1] } },
};
const stats = [
  { value: '4', label: 'Asset Classes' },
  { value: '<1s', label: 'Latency' },
  { value: '24/7', label: 'Uptime' },
];
const Home = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const { isDark } = useTheme();
  return (
    <div className="relative">
      {}
      <section className="relative pt-16 pb-20 md:pt-24 md:pb-28 flex flex-col items-center text-center">
        {}
        {isDark && (
          <div
            className="pointer-events-none absolute top-[-120px] left-1/2 -translate-x-1/2 w-[520px] h-[260px] rounded-full blur-[120px]"
            style={{ background: 'radial-gradient(circle, rgba(94,106,210,0.18) 0%, transparent 70%)' }}
            aria-hidden="true"
          />
        )}
        {}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.05, ease: [0.16, 1, 0.3, 1] }}
          className="mb-6"
        >
          <span className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-medium border border-border-default bg-surface text-fg-muted">
            <Zap size={12} className="text-accent" />
            Real-time financial intelligence
          </span>
        </motion.div>
        {}
        <motion.h1
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.12, ease: [0.16, 1, 0.3, 1] }}
          className="text-4xl md:text-5xl lg:text-6xl font-bold tracking-[-0.035em] leading-[1.1] text-fg max-w-2xl"
        >
          Markets, decoded.{' '}
          <span className="text-gradient">In real time.</span>
        </motion.h1>
        {}
        <motion.p
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, delay: 0.2, ease: [0.16, 1, 0.3, 1] }}
          className="mt-5 text-base md:text-lg text-fg-muted max-w-lg leading-relaxed"
        >
          One portal for stocks, crypto, forex &amp; metals — with institutional-grade
          charts, enterprise SSO, and zero config.
        </motion.p>
        {}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.28, ease: [0.16, 1, 0.3, 1] }}
          className="mt-9 flex flex-wrap items-center justify-center gap-3"
        >
          {!isAuthenticated ? (
            <>
              <button
                onClick={() => navigate('/login')}
                className="group relative flex items-center gap-2 px-6 py-2.5 text-sm font-semibold border-none rounded-lg cursor-pointer bg-accent text-white hover:bg-accent-bright transition-all duration-150 active:scale-[0.97]"
                style={{
                  boxShadow: isDark
                    ? '0 0 0 1px rgba(94,106,210,0.5), 0 2px 12px rgba(94,106,210,0.30), inset 0 1px 0 0 rgba(255,255,255,0.12)'
                    : undefined,
                }}
              >
                <LogIn size={15} strokeWidth={1.6} className="opacity-80 group-hover:opacity-100 transition-opacity" />
                Get Started
                <ArrowRight size={14} strokeWidth={2} className="ml-0.5 opacity-0 -translate-x-1 group-hover:opacity-80 group-hover:translate-x-0 transition-all duration-150" />
              </button>
              <button
                onClick={() => navigate('/register')}
                className="group flex items-center gap-2 px-5 py-2.5 text-sm font-semibold rounded-lg cursor-pointer bg-transparent text-fg border border-border-default hover:bg-surface hover:border-border-hover transition-all duration-150"
              >
                <UserPlus size={15} strokeWidth={1.6} className="text-transparent group-hover:text-fg-muted transition-colors duration-150" />
                Create account
              </button>
            </>
          ) : (
            <button
              onClick={() => navigate('/market')}
              className="group relative flex items-center gap-2 px-6 py-2.5 text-sm font-semibold border-none rounded-lg cursor-pointer bg-accent text-white hover:bg-accent-bright transition-all duration-150 active:scale-[0.97]"
              style={{
                boxShadow: isDark
                  ? '0 0 0 1px rgba(94,106,210,0.5), 0 2px 12px rgba(94,106,210,0.30), inset 0 1px 0 0 rgba(255,255,255,0.12)'
                  : undefined,
              }}
            >
              <Globe size={15} strokeWidth={1.6} className="opacity-80 group-hover:opacity-100 transition-opacity" />
              Open Dashboard
              <ArrowRight size={14} strokeWidth={2} className="ml-0.5 opacity-0 -translate-x-1 group-hover:opacity-80 group-hover:translate-x-0 transition-all duration-150" />
            </button>
          )}
        </motion.div>
        {}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.5, delay: 0.4 }}
          className="mt-14 flex items-center gap-8"
        >
          {stats.map((s, i) => (
            <div key={i} className="flex flex-col items-center gap-0.5">
              <span className="text-xl md:text-2xl font-bold text-fg tracking-tight">{s.value}</span>
              <span className="text-[11px] text-fg-subtle uppercase tracking-widest">{s.label}</span>
            </div>
          ))}
        </motion.div>
      </section>
      {}
      <div className="section-line" />
      {}
      <section className="py-16 md:py-20">
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-60px' }}
          transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
          className="text-center mb-12"
        >
          <h2 className="text-2xl md:text-3xl font-bold tracking-[-0.025em] text-fg">
            Everything you need.{' '}
            <span className="text-fg-muted font-normal">Nothing you don&rsquo;t.</span>
          </h2>
        </motion.div>
        <motion.div
          variants={containerV}
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, margin: '-40px' }}
          className="grid grid-cols-1 md:grid-cols-3 gap-3"
        >
          {features.map((f) => {
            const Icon = f.icon;
            return (
              <motion.div
                key={f.title}
                variants={itemV}
                className={`group relative rounded-xl border border-border-default bg-bg-elevated p-6 md:p-7 transition-all duration-200 hover:border-border-hover card-hover overflow-hidden ${f.span}`}
              >
                {}
                {isDark && (
                  <span className="pointer-events-none absolute -top-24 -right-24 w-56 h-56 rounded-full bg-accent/[0.06] blur-[80px] opacity-0 group-hover:opacity-100 transition-opacity duration-500" aria-hidden="true" />
                )}
                <span className="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent mb-4 transition-colors duration-200 group-hover:bg-accent/20">
                  <Icon size={18} strokeWidth={1.5} />
                </span>
                <h3 className="text-[15px] font-semibold text-fg mb-1.5">{f.title}</h3>
                <p className="text-[13px] text-fg-muted leading-relaxed">{f.description}</p>
              </motion.div>
            );
          })}
        </motion.div>
      </section>
      {}
      {!isAuthenticated && (
        <>
          <div className="section-line" />
          <section className="py-16 md:py-20 flex flex-col items-center text-center">
            <motion.div
              initial={{ opacity: 0, y: 12 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-60px' }}
              transition={{ duration: 0.45 }}
            >
              <h2 className="text-2xl md:text-3xl font-bold tracking-[-0.025em] text-fg mb-3">
                Ready to trade smarter?
              </h2>
              <p className="text-fg-muted mb-8 max-w-sm mx-auto text-sm leading-relaxed">
                Join in seconds — no credit card, no commitment.
              </p>
              <button
                onClick={() => navigate('/register')}
                className="group relative inline-flex items-center gap-2 px-7 py-3 text-sm font-semibold border-none rounded-lg cursor-pointer bg-accent text-white hover:bg-accent-bright transition-all duration-150 active:scale-[0.97]"
                style={{
                  boxShadow: isDark
                    ? '0 0 0 1px rgba(94,106,210,0.5), 0 4px 20px rgba(94,106,210,0.25), inset 0 1px 0 0 rgba(255,255,255,0.12)'
                    : undefined,
                }}
              >
                Create free account
                <ArrowRight size={15} strokeWidth={2} className="opacity-0 -translate-x-1 group-hover:opacity-80 group-hover:translate-x-0 transition-all duration-150" />
              </button>
            </motion.div>
          </section>
        </>
      )}
    </div>
  );
};
export default Home;
