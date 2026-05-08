import { cardVariants } from '../../../shared/utils/animations';
import { relTime } from '../util';

export default function MessageBubble({ message, leftSide, label }) {
  return (
    <motion.div
      variants={cardVariants}
      className={`flex ${leftSide ? 'justify-start' : 'justify-end'}`}
    >
      <motion.div
        whileHover={{ y: -1 }}
        transition={{ type: 'spring', stiffness: 500, damping: 30 }}
        className={`max-w-[78%] relative rounded-2xl px-4 py-3 text-sm leading-relaxed border card-hover transition-all ${
          leftSide
            ? 'bg-surface border-border-default text-fg hover:border-border-hover'
            : 'bg-gradient-to-br from-accent/15 to-accent/5 border-accent/30 text-fg shadow-md shadow-accent/15 hover:shadow-accent/25 hover:border-accent/50'
        }`}
      >
        {label && (
          <div className="flex items-center gap-1.5 mb-2">
            <span className="flex items-center justify-center w-4 h-4 rounded-full bg-accent/20">
              <span className="w-1.5 h-1.5 rounded-full bg-accent" />
            </span>
            <span className="text-[10px] font-mono uppercase tracking-wider text-accent font-bold">{label}</span>
          </div>
        )}
        <p className="whitespace-pre-wrap break-words">{message.body}</p>
        <div className="mt-2 text-[10px] font-mono text-fg-subtle">{relTime(message.sentAt)}</div>
      </motion.div>
    </motion.div>
  );
}
