import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import Spinner from './Spinner';

const cx = (...parts) => parts.filter(Boolean).join(' ');

/**
 * @typedef {Object} LoadingStateProps
 * @property {React.ReactNode} [message]
 * @property {boolean} [fullscreen=true]
 * @property {'xs'|'sm'|'md'|'lg'} [size='lg']
 * @property {string} [className]
 */

/** @param {LoadingStateProps} props */
export default function LoadingState({ message, fullscreen = true, size = 'lg', className }) {
    const { t } = useTranslation();
    const text = message ?? t('common.loadingData');
    return (
        <div className={cx(fullscreen ? 'flex min-h-[60vh] items-center justify-center' : 'flex items-center justify-center py-8', className)}>
            <motion.div
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                className={cx(
                    'flex flex-col items-center gap-4 rounded-2xl border border-border-default bg-bg-elevated card-hover',
                    fullscreen ? 'px-10 py-10' : 'px-6 py-6',
                )}
            >
                <div className="relative">
                    <div className="absolute inset-0 rounded-full bg-accent/20 blur-xl animate-pulse-glow" />
                    <Spinner size={size} tone="accent" className="relative" />
                </div>
                <span className="text-fg-muted text-sm font-medium">{text}</span>
            </motion.div>
        </div>
    );
}
