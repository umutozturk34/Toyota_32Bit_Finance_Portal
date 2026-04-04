import { motion } from 'framer-motion';
import { Loader2 } from 'lucide-react';

export default function LoadingState({ message = 'Veriler yükleniyor…' }) {
    return (
        <div className="flex min-h-[60vh] items-center justify-center">
            <motion.div
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                className="flex flex-col items-center gap-3"
            >
                <Loader2 className="h-8 w-8 animate-spin text-accent" />
                <span className="text-fg-muted text-sm">{message}</span>
            </motion.div>
        </div>
    );
}
