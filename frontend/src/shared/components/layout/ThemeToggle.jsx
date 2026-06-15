import { motion, AnimatePresence } from 'framer-motion';
import { Sun, Moon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../context/useTheme';

/**
 * Sidebar-footer theme switch. Lives OUTSIDE settings so it's always one click away, and its click point is what
 * the circular-reveal ripple (see ThemeContext.runThemeTransition) radiates from. The icon cross-rotates on toggle:
 * a sun when dark (tap → go light), a moon when light. Matches the surrounding footer-button styling.
 */
export default function ThemeToggle({ collapsed, isMobile }) {
  const { t } = useTranslation();
  const { isDark, toggleTheme } = useTheme();
  const label = t(isDark ? 'theme.LIGHT' : 'theme.DARK');

  return (
    <button
      type="button"
      onClick={toggleTheme}
      title={collapsed && !isMobile ? label : undefined}
      aria-label={label}
      className="w-full group flex items-center overflow-hidden px-0 py-2 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer"
    >
      <span className="flex items-center justify-center w-12 shrink-0">
        <AnimatePresence mode="wait" initial={false}>
          <motion.span
            key={isDark ? 'sun' : 'moon'}
            initial={{ rotate: -90, opacity: 0, scale: 0.6 }}
            animate={{ rotate: 0, opacity: 1, scale: 1 }}
            exit={{ rotate: 90, opacity: 0, scale: 0.6 }}
            transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
            className="inline-flex text-fg-muted group-hover:text-accent transition-colors"
          >
            {isDark ? <Sun size={16} strokeWidth={1.6} /> : <Moon size={16} strokeWidth={1.6} />}
          </motion.span>
        </AnimatePresence>
      </span>
      {(!collapsed || isMobile) && <span className="text-[13px] font-medium">{label}</span>}
    </button>
  );
}
