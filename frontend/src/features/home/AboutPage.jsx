import { Code, Database, Server, Key, Container } from 'lucide-react';
import { motion } from 'framer-motion';
import { useTheme } from '../../shared/context/ThemeContext';
const techStack = [
  { icon: Code, label: 'Frontend', value: 'React 19 + Vite' },
  { icon: Server, label: 'Backend', value: 'Spring Boot 3.4' },
  { icon: Database, label: 'Database', value: 'PostgreSQL 15' },
  { icon: Key, label: 'Authentication', value: 'Keycloak' },
  { icon: Container, label: 'Deployment', value: 'Docker Compose' },
];
const containerV = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06, delayChildren: 0.15 } },
};
const itemV = {
  hidden: { opacity: 0, y: 12 },
  show:   { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] } },
};
const AboutPage = () => {
  const { isDark } = useTheme();
  return (
    <div className="max-w-3xl mx-auto py-12 md:py-16">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        className="mb-10"
      >
        <h1 className="text-3xl md:text-4xl font-bold tracking-[-0.03em] text-fg">
          About <span className="text-gradient">Finance Portal</span>
        </h1>
        <p className="text-fg-muted text-sm mt-2 max-w-lg leading-relaxed">
          A comprehensive financial management system with real-time market data,
          enterprise-grade security, and advanced charting.
        </p>
      </motion.div>
      <div className="space-y-4">
        {}
        <motion.section
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.1, ease: [0.16, 1, 0.3, 1] }}
          className="relative rounded-xl border border-border-default bg-bg-elevated p-6 card-hover overflow-hidden"
        >
          {isDark && (
            <span className="pointer-events-none absolute -top-16 -right-16 w-40 h-40 rounded-full bg-accent/[0.05] blur-[60px]" aria-hidden="true" />
          )}
          <h2 className="text-lg font-semibold text-fg mb-3">Project Overview</h2>
          <p className="text-fg-muted text-sm leading-relaxed">
            Toyota Finance Portal is a comprehensive financial management system
            designed for Toyota dealerships and financial services. This application
            provides secure authentication, real-time market data, and portfolio
            management capabilities.
          </p>
        </motion.section>
        {}
        <div className="section-line" />
        {}
        <motion.section
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.2, ease: [0.16, 1, 0.3, 1] }}
          className="relative rounded-xl border border-border-default bg-bg-elevated p-6 card-hover overflow-hidden"
        >
          {isDark && (
            <span className="pointer-events-none absolute -bottom-16 -left-16 w-40 h-40 rounded-full bg-[#7c3aed]/[0.04] blur-[60px]" aria-hidden="true" />
          )}
          <h2 className="text-lg font-semibold text-fg mb-5">Technology Stack</h2>
          <motion.div
            variants={containerV}
            initial="hidden"
            animate="show"
            className="flex flex-col gap-2"
          >
            {techStack.map((item) => {
              const Icon = item.icon;
              return (
                <motion.div
                  key={item.label}
                  variants={itemV}
                  className="group flex items-center gap-4 bg-bg-base hover:bg-surface border border-border-default rounded-lg p-3.5 transition-all duration-150"
                >
                  <span className="flex items-center justify-center w-8 h-8 rounded-md bg-accent/10 text-accent group-hover:bg-accent/20 transition-colors duration-150 shrink-0">
                    <Icon size={16} strokeWidth={1.8} />
                  </span>
                  <span className="text-fg font-medium text-sm min-w-[110px]">{item.label}</span>
                  <span className="text-fg-muted text-sm">{item.value}</span>
                </motion.div>
              );
            })}
          </motion.div>
        </motion.section>
        {}
        <motion.section
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.3, ease: [0.16, 1, 0.3, 1] }}
          className="rounded-xl border border-border-default bg-bg-elevated p-6 card-hover"
        >
          <h2 className="text-lg font-semibold text-fg mb-3">Version</h2>
          <p className="text-fg-muted text-sm">
            Current Version:{' '}
            <span className="inline-block px-2.5 py-0.5 bg-accent/15 text-accent-bright rounded-full text-xs font-semibold border border-border-accent">
              v0.1.0
            </span>{' '}
            <span className="text-fg-subtle">(In Development)</span>
          </p>
        </motion.section>
      </div>
    </div>
  );
};
export default AboutPage;
