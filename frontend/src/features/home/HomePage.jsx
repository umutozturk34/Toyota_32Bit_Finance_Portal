import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTheme } from '../../shared/context/ThemeContext';
import { useAuth } from '../auth/AuthContext';
import { motion } from 'framer-motion';
import { unifiedMarketService } from '../../shared/services/unifiedMarketService';
import { formatPriceTRY } from '../../shared/utils/formatters';
import {
  Shield, BarChart3, Briefcase, UserPlus, LogIn,
  TrendingUp, Zap, LineChart, ArrowRight,
  Activity, Lock, Layers, Sun, Moon,
} from 'lucide-react';

const easeOut = [0.16, 1, 0.3, 1];

const features = [
  {
    icon: Shield,
    title: 'Bank-Grade Security',
    description: 'Keycloak SSO, LDAP federation, 2FA/TOTP — your credentials never touch our servers.',
    glowPos: 'top-left',
    glowColor: { dark: 'rgba(99,102,241,0.18)', light: 'rgba(0,82,255,0.12)' },
  },
  {
    icon: BarChart3,
    title: 'Live Market Data',
    description: 'Real-time quotes across stocks, crypto, forex & metals with sub-second latency.',
    glowPos: 'top-right',
    glowColor: { dark: 'rgba(16,185,129,0.16)', light: 'rgba(16,163,127,0.12)' },
  },
  {
    icon: LineChart,
    title: 'Advanced Charting',
    description: 'TradingView charts with indicators, drawing tools, Fibonacci and compare mode.',
    glowPos: 'bottom-left',
    glowColor: { dark: 'rgba(245,158,11,0.14)', light: 'rgba(234,138,0,0.10)' },
  },
  {
    icon: Briefcase,
    title: 'Portfolio Tracking',
    description: 'Build portfolios, track P&L, and monitor positions across every asset class.',
    glowPos: 'bottom-right',
    glowColor: { dark: 'rgba(168,85,247,0.16)', light: 'rgba(109,40,217,0.10)' },
  },
];

const GLOW_POSITIONS = {
  'top-left': { top: '-40%', left: '-40%' },
  'top-right': { top: '-40%', right: '-40%' },
  'bottom-left': { bottom: '-40%', left: '-40%' },
  'bottom-right': { bottom: '-40%', right: '-40%' },
};

const stats = [
  { value: '4', label: 'Asset Classes', icon: Layers },
  { value: '<1s', label: 'Latency', icon: Zap },
  { value: '24/7', label: 'Uptime', icon: Activity },
  { value: '2FA', label: 'Security', icon: Lock },
];

const CARD_POSITIONS = [
  { position: 'top-[12%] left-[8%]', delay: 0, duration: 5, y: [-8, 8, -8] },
  { position: 'top-[18%] right-[6%]', delay: 1, duration: 4, y: [6, -6, 6] },
  { position: 'bottom-[18%] left-[12%]', delay: 0.5, duration: 4.5, y: [-5, 10, -5] },
];

const STATIC_CARDS = [
  { label: 'BIST 100', price: '9.847,32 ₺', change: '+2.14%', changeColor: 'text-success', iconBg: 'from-emerald-500 to-teal-500' },
  { label: 'BTC', price: '67.241,00 ₺', change: '+1.82%', changeColor: 'text-success', iconBg: 'from-amber-500 to-orange-500' },
  { label: 'USD/TRY', price: '38,42 ₺', change: '-0.31%', changeColor: 'text-danger', iconBg: 'from-blue-500 to-cyan-500' },
];

function buildFloatingCards(overview) {
  if (!overview) return STATIC_CARDS;
  const picks = [];
  if (overview.indices?.[0]) picks.push(overview.indices[0]);
  const firstMovers = overview.movers?.[0];
  if (firstMovers?.gainers?.[0]) picks.push(firstMovers.gainers[0]);
  if (firstMovers?.losers?.[0]) picks.push(firstMovers.losers[0]);
  if (picks.length === 0) return STATIC_CARDS;

  const gradients = ['from-emerald-500 to-teal-500', 'from-amber-500 to-orange-500', 'from-blue-500 to-cyan-500'];
  return picks.slice(0, 3).map((asset, i) => {
    const pct = asset.changePercent ?? 0;
    return {
      label: asset.name || asset.code,
      price: formatPriceTRY(asset.price),
      change: `${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%`,
      changeColor: pct >= 0 ? 'text-success' : 'text-danger',
      iconBg: gradients[i],
    };
  });
}

function HeroGraphic({ isDark, cards }) {
  const accentColor = isDark ? 'rgba(99,102,241,' : 'rgba(0,82,255,';
  return (
    <div className="relative w-full h-full min-h-[340px] lg:min-h-[420px]">
      <div
        className="absolute inset-0 rounded-[2rem]"
        style={{
          background: `radial-gradient(ellipse at 30% 20%, ${accentColor}0.15) 0%, transparent 60%)`,
        }}
      />

      <svg
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[300px] h-[300px] lg:w-[360px] lg:h-[360px] animate-spin-slow opacity-15"
        viewBox="0 0 200 200"
        fill="none"
      >
        <circle cx="100" cy="100" r="90" stroke={isDark ? '#6366f1' : '#0052FF'} strokeWidth="0.5" strokeDasharray="4 6" />
        <circle cx="100" cy="100" r="70" stroke={isDark ? '#818cf8' : '#4D7CFF'} strokeWidth="0.5" strokeDasharray="3 8" />
        <circle cx="100" cy="100" r="50" stroke={isDark ? '#a78bfa' : '#80AAFF'} strokeWidth="0.3" strokeDasharray="2 10" />
      </svg>

      {cards.map((card, i) => {
        const pos = CARD_POSITIONS[i] || CARD_POSITIONS[0];
        return (
        <motion.div
          key={i}
          animate={{ y: pos.y }}
          transition={{ duration: pos.duration, repeat: Infinity, ease: "easeInOut", delay: pos.delay }}
          className={`absolute ${pos.position} w-36 rounded-xl border bg-bg-elevated backdrop-blur-md p-3`}
          style={{
            borderColor: isDark ? 'rgba(99,102,241,0.2)' : 'rgba(0,82,255,0.15)',
            boxShadow: isDark
              ? '0 8px 32px rgba(0,0,0,0.4), 0 0 60px rgba(99,102,241,0.12), inset 0 0 30px rgba(99,102,241,0.04)'
              : '0 8px 32px rgba(0,0,0,0.1), 0 0 60px rgba(0,82,255,0.1), inset 0 0 30px rgba(0,82,255,0.03)',
          }}
        >
          <div className="flex items-center gap-2 mb-2">
            <span className={`w-5 h-5 rounded-md bg-gradient-to-br ${card.iconBg}`} />
            <span className="text-[10px] font-medium text-fg-muted">{card.label}</span>
          </div>
          <p className="text-sm font-bold font-mono text-fg">{card.price}</p>
          <span className={`text-[10px] font-mono ${card.changeColor}`}>{card.change}</span>
        </motion.div>
        );
      })}

      <motion.div
        animate={{ y: [4, -8, 4] }}
        transition={{ duration: 5.5, repeat: Infinity, ease: "easeInOut", delay: 2 }}
        className="absolute bottom-[25%] right-[12%] w-10 h-10 rounded-lg bg-gradient-to-br from-accent to-accent-bright shadow-lg shadow-accent/20"
      />

      <div className="absolute top-[55%] left-[48%] grid grid-cols-3 gap-2">
        {[...Array(9)].map((_, i) => (
          <motion.span
            key={i}
            animate={{ opacity: [0.15, 0.4, 0.15] }}
            transition={{ duration: 2, repeat: Infinity, delay: i * 0.2 }}
            className="w-1.5 h-1.5 rounded-full bg-accent"
          />
        ))}
      </div>
    </div>
  );
}

function AnimatedDotGrid({ isDark }) {
  const rows = 5;
  const cols = 12;
  return (
    <div className="flex items-center justify-center py-4">
      <div className="grid gap-4" style={{ gridTemplateColumns: `repeat(${cols}, 6px)` }}>
        {Array.from({ length: rows * cols }).map((_, i) => {
          const row = Math.floor(i / cols);
          const col = i % cols;
          const distFromCenter = Math.sqrt(Math.pow(row - rows / 2, 2) + Math.pow(col - cols / 2, 2));
          return (
            <motion.span
              key={i}
              animate={{
                opacity: [0.08, 0.35, 0.08],
                scale: [1, 1.3, 1],
              }}
              transition={{
                duration: 3,
                repeat: Infinity,
                delay: distFromCenter * 0.15,
                ease: 'easeInOut',
              }}
              className="w-1.5 h-1.5 rounded-full"
              style={{ background: isDark ? '#6366f1' : '#0052FF' }}
            />
          );
        })}
      </div>
    </div>
  );
}

const HomePage = () => {
  const navigate = useNavigate();
  const { isDark, toggleTheme } = useTheme();
  const { isAuthenticated } = useAuth();

  const { data: overview } = useQuery({
    queryKey: ['marketOverview'],
    queryFn: () => unifiedMarketService.getOverview(),
    enabled: isAuthenticated,
    staleTime: 60_000,
  });

  const floatingCards = useMemo(() => buildFloatingCards(overview), [overview]);

  return (
    <div className="min-h-screen bg-bg-base relative overflow-hidden">
      <div className="fixed top-5 right-5 z-50">
        <button
          onClick={toggleTheme}
          className="flex items-center justify-center w-9 h-9 rounded-lg border border-border-default bg-bg-elevated backdrop-blur-md text-fg-muted hover:text-fg hover:bg-surface transition-all cursor-pointer"
        >
          {isDark ? <Sun size={15} /> : <Moon size={15} />}
        </button>
      </div>

      {isDark && (
        <>
          <div
            className="pointer-events-none absolute top-[-120px] left-[20%] w-[600px] h-[500px] rounded-full blur-[180px]"
            style={{ background: 'radial-gradient(circle, rgba(99,102,241,0.1) 0%, transparent 70%)' }}
            aria-hidden="true"
          />
          <div
            className="pointer-events-none absolute bottom-[-80px] right-[15%] w-[400px] h-[350px] rounded-full blur-[140px]"
            style={{ background: 'radial-gradient(circle, rgba(124,58,237,0.07) 0%, transparent 70%)' }}
            aria-hidden="true"
          />
        </>
      )}
      {!isDark && (
        <>
          <div
            className="pointer-events-none absolute top-[-80px] right-[20%] w-[500px] h-[450px] rounded-full blur-[160px]"
            style={{ background: 'radial-gradient(circle, rgba(0,82,255,0.06) 0%, transparent 70%)' }}
            aria-hidden="true"
          />
          <div
            className="pointer-events-none absolute bottom-[20%] left-[10%] w-[300px] h-[300px] rounded-full blur-[120px]"
            style={{ background: 'radial-gradient(circle, rgba(0,82,255,0.04) 0%, transparent 70%)' }}
            aria-hidden="true"
          />
        </>
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
                  Live Market Intelligence
                </span>
              </motion.div>

              <motion.h1
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, delay: 0.12, ease: easeOut }}
                className="text-[2.5rem] md:text-[3.25rem] lg:text-[3.75rem] font-display leading-[1.08] tracking-[-0.02em] text-fg"
              >
                Markets, decoded.{' '}
                <span className="relative inline-block">
                  <span className="text-gradient">In real time.</span>
                  <span className="absolute bottom-[-0.15rem] md:bottom-[-0.3rem] left-0 h-2 md:h-2.5 w-full rounded-sm bg-gradient-to-r from-accent/15 to-accent-bright/10" />
                </span>
              </motion.h1>

              <motion.p
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.22, ease: easeOut }}
                className="text-base md:text-lg text-fg-muted max-w-lg leading-relaxed mx-auto lg:mx-0"
              >
                One portal for stocks, crypto, forex &amp; metals — with institutional-grade
                charts, enterprise SSO, and zero config.
              </motion.p>

              <motion.div
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.3, ease: easeOut }}
                className="flex flex-wrap items-center gap-3 justify-center lg:justify-start"
              >
                <button
                  onClick={() => navigate('/register')}
                  className="group relative flex items-center gap-2 px-7 py-3 text-sm font-semibold border-none rounded-xl cursor-pointer bg-gradient-accent text-white transition-all duration-200 hover:-translate-y-0.5 active:scale-[0.98]"
                  style={{
                    boxShadow: isDark
                      ? '0 4px 20px rgba(99,102,241,0.35)'
                      : '0 4px 20px rgba(0,82,255,0.3)',
                  }}
                >
                  <UserPlus size={15} strokeWidth={1.6} />
                  Create free account
                  <ArrowRight size={14} strokeWidth={2} className="ml-0.5 opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-200" />
                </button>
                <button
                  onClick={() => navigate('/login')}
                  className="group flex items-center gap-2 px-6 py-3 text-sm font-semibold rounded-xl cursor-pointer bg-transparent text-fg border border-border-default hover:bg-surface hover:border-border-hover transition-all duration-200 hover:-translate-y-0.5"
                >
                  <LogIn size={15} strokeWidth={1.6} />
                  Sign in
                </button>
              </motion.div>

              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.6, delay: 0.5 }}
                className="flex items-center gap-5 sm:gap-6 pt-2 justify-center lg:justify-start flex-wrap"
              >
                {stats.map((s, i) => {
                  const Icon = s.icon;
                  return (
                    <div key={i} className="flex items-center gap-1.5">
                      <Icon size={13} className="text-accent" />
                      <span className="text-base font-bold text-fg tracking-tight">{s.value}</span>
                      <span className="text-[10px] text-fg-subtle uppercase tracking-wider">{s.label}</span>
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
              Features
            </span>
            <h2 className="text-3xl md:text-[2.5rem] font-display tracking-normal text-fg">
              Everything you need.{' '}
              <span className="text-fg-muted">Nothing you don&rsquo;t.</span>
            </h2>
          </motion.div>

          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, margin: '-40px' }}
            variants={{ hidden: {}, visible: { transition: { staggerChildren: 0.1 } } }}
            className="grid grid-cols-1 md:grid-cols-2 gap-4"
          >
            {features.map((f) => {
              const Icon = f.icon;
              return (
                <motion.div
                  key={f.title}
                  variants={{
                    hidden: { opacity: 0, y: 28 },
                    visible: { opacity: 1, y: 0, transition: { duration: 0.7, ease: easeOut } },
                  }}
                  className="group relative rounded-2xl border bg-bg-elevated backdrop-blur-md p-8 transition-all duration-300 card-hover overflow-hidden"
                  style={{
                    borderColor: isDark ? 'rgba(99,102,241,0.15)' : 'rgba(0,82,255,0.12)',
                  }}
                >
                  <div
                    className="pointer-events-none absolute w-[200px] h-[200px] rounded-full blur-[80px] opacity-80 group-hover:opacity-100 transition-opacity duration-500"
                    style={{
                      ...GLOW_POSITIONS[f.glowPos],
                      background: `radial-gradient(circle, ${isDark ? f.glowColor.dark : f.glowColor.light} 0%, transparent 70%)`,
                    }}
                    aria-hidden="true"
                  />
                  <span className="relative inline-flex items-center justify-center w-11 h-11 rounded-xl bg-gradient-accent text-white mb-5 shadow-lg shadow-accent/20 transition-transform duration-300 group-hover:scale-110">
                    <Icon size={20} strokeWidth={1.5} />
                  </span>
                  <h3 className="relative text-lg font-semibold text-fg mb-2">{f.title}</h3>
                  <p className="relative text-sm text-fg-muted leading-relaxed">{f.description}</p>
                </motion.div>
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
              Get Started
            </span>

            <h2 className="text-3xl md:text-4xl font-display text-white mb-4">
              Ready to trade smarter?
            </h2>
            <p className="text-white/60 mb-10 max-w-md mx-auto text-base leading-relaxed">
              Join in seconds — no credit card, no commitment.
            </p>

            <div className="flex flex-wrap items-center gap-3 justify-center">
              <button
                onClick={() => navigate('/register')}
                className="group relative inline-flex items-center gap-2 px-8 py-3.5 text-sm font-semibold border-none rounded-xl cursor-pointer bg-white text-[#0F172A] transition-all duration-200 hover:-translate-y-0.5 hover:shadow-xl active:scale-[0.98]"
              >
                <UserPlus size={15} strokeWidth={1.6} />
                Create free account
                <ArrowRight size={15} strokeWidth={2} className="opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-200" />
              </button>
              <button
                onClick={() => navigate('/login')}
                className="group inline-flex items-center gap-2 px-6 py-3.5 text-sm font-semibold rounded-xl cursor-pointer bg-transparent text-white/80 border border-white/15 hover:bg-white/5 hover:border-white/25 hover:text-white transition-all duration-200 hover:-translate-y-0.5"
              >
                <LogIn size={15} strokeWidth={1.6} />
                Sign in
              </button>
            </div>
          </motion.div>

          <AnimatedDotGrid isDark={!isDark} />
        </section>

        <footer className="py-8 text-center">
          <p className="text-xs text-fg-subtle">&copy; 2026 Finance Portal</p>
        </footer>
      </div>
    </div>
  );
};

export default HomePage;
