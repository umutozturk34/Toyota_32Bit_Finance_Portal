import { motion } from 'framer-motion';
import { Check } from 'lucide-react';

export default function SelectableCheckbox({ checked, onClick, label }) {
    return (
        <motion.button
            type="button"
            role="checkbox"
            aria-checked={checked}
            aria-label={label}
            onClick={(e) => { e.stopPropagation(); onClick(e); }}
            whileTap={{ scale: 0.85 }}
            className={`inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-md border-2 transition-colors cursor-pointer ${
                checked ? 'border-accent bg-accent' : 'border-border-default/70 hover:border-accent/60 bg-bg-base/40'
            }`}
        >
            <motion.span
                initial={false}
                animate={{ scale: checked ? 1 : 0 }}
                transition={{ type: 'spring', stiffness: 600, damping: 24 }}
                className="inline-flex"
            >
                <Check className="h-3 w-3 text-white" strokeWidth={3.5} />
            </motion.span>
        </motion.button>
    );
}
