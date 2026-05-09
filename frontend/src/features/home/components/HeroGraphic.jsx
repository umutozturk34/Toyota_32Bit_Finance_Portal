import { CARD_POSITIONS } from '../lib/homePageConstants';

import { motion } from 'framer-motion';
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

export { HeroGraphic, AnimatedDotGrid };
