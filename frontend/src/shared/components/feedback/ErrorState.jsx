import { AlertTriangle, RefreshCw } from './AnimatedIcons';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import Button from '../buttons/Button';

const cx = (...parts) => parts.filter(Boolean).join(' ');

/**
 * @typedef {Object} ErrorStateProps
 * @property {React.ReactNode} message
 * @property {() => void} [onRetry]
 * @property {boolean} [fullscreen=true]
 * @property {string} [className]
 */

/** @param {ErrorStateProps} props */
export default function ErrorState({ message, onRetry, fullscreen = true, className }) {
    const { t } = useTranslation();
    return (
        <div className={cx(
            fullscreen
                ? 'flex min-h-[60vh] flex-col items-center justify-center gap-5'
                : 'flex flex-col items-center justify-center gap-3 py-8',
            className,
        )}>
            <motion.div
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                className={cx(
                    'flex flex-col items-center gap-4 rounded-2xl border border-danger/20 bg-bg-elevated card-hover',
                    fullscreen ? 'px-8 py-8' : 'px-6 py-6',
                )}
            >
                <div className={cx('flex items-center justify-center rounded-xl bg-danger/10', fullscreen ? 'w-14 h-14' : 'w-10 h-10')}>
                    <AlertTriangle className={cx('text-danger', fullscreen ? 'h-7 w-7' : 'h-5 w-5')} />
                </div>
                <div className="text-center space-y-1">
                    <p className="text-sm font-semibold text-fg">{t('errorState.title')}</p>
                    <p className="text-xs text-fg-muted max-w-xs">{message}</p>
                </div>
                {onRetry && (
                    <Button variant="secondary" size="md" leftIcon={<RefreshCw className="h-4 w-4" />} onClick={onRetry}>
                        {t('common.retry')}
                    </Button>
                )}
            </motion.div>
        </div>
    );
}
