import { motion } from 'framer-motion';

const draw = {
  hidden: { pathLength: 0, opacity: 0 },
  visible: (d = 0) => ({
    pathLength: 1,
    opacity: 1,
    transition: { pathLength: { delay: d, duration: 0.5, ease: 'easeInOut' }, opacity: { delay: d, duration: 0.1 } },
  }),
};

const pop = {
  hidden: { scale: 0, opacity: 0 },
  visible: (d = 0) => ({
    scale: 1,
    opacity: 1,
    transition: { delay: d, duration: 0.3, type: 'spring', stiffness: 400, damping: 15 },
  }),
};

const slide = (x = 0, y = 0) => ({
  hidden: { x, y, opacity: 0 },
  visible: (d = 0) => ({
    x: 0, y: 0, opacity: 1,
    transition: { delay: d, duration: 0.35, ease: [0.16, 1, 0.3, 1] },
  }),
});

export function Loader2({ className, ...props }) {
  return (
    <motion.svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} {...props}>
      <motion.path
        d="M21 12a9 9 0 1 1-6.219-8.56"
        animate={{ rotate: 360 }}
        transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
        style={{ transformOrigin: '12px 12px' }}
      />
    </motion.svg>
  );
}

export function RefreshCw({ className, ...props }) {
  const isSpinning = className?.includes('animate-spin');
  const cleanClass = className?.replace(/animate-spin/g, '').trim();
  return (
    <motion.svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={cleanClass} initial="hidden" animate="visible" {...props}>
      <motion.g
        animate={isSpinning ? { rotate: 360 } : {}}
        transition={isSpinning ? { duration: 0.8, repeat: Infinity, ease: 'linear' } : {}}
        style={{ transformOrigin: '12px 12px' }}
      >
        <motion.path d="M21 2v6h-6" variants={draw} custom={0} />
        <motion.path d="M3 12a9 9 0 0 1 15-6.7L21 8" variants={draw} custom={0.1} />
        <motion.path d="M3 22v-6h6" variants={draw} custom={0.2} />
        <motion.path d="M21 12a9 9 0 0 1-15 6.7L3 16" variants={draw} custom={0.3} />
      </motion.g>
    </motion.svg>
  );
}

export function TrendingUp({ className, ...props }) {
  return (
    <motion.svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} initial="hidden" animate="visible" {...props}>
      <motion.path d="M22 7 13.5 15.5 8.5 10.5 2 17" variants={draw} custom={0} />
      <motion.path d="M16 7h6v6" variants={draw} custom={0.4} />
    </motion.svg>
  );
}

export function TrendingDown({ className, ...props }) {
  return (
    <motion.svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} initial="hidden" animate="visible" {...props}>
      <motion.path d="M22 17 13.5 8.5 8.5 13.5 2 7" variants={draw} custom={0} />
      <motion.path d="M16 17h6v-6" variants={draw} custom={0.4} />
    </motion.svg>
  );
}

export function Check({ className, strokeWidth = 2, ...props }) {
  return (
    <motion.svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" className={className} initial="hidden" animate="visible" {...props}>
      <motion.path d="M20 6 9 17l-5-5" variants={draw} custom={0.1} />
    </motion.svg>
  );
}

export function AlertTriangle({ className, ...props }) {
  return (
    <motion.svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} initial="hidden" animate="visible" {...props}>
      <motion.path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3" variants={draw} custom={0} />
      <motion.path d="M12 9v4" variants={draw} custom={0.4} />
      <motion.circle cx="12" cy="17" r="0.5" fill="currentColor" stroke="none" variants={pop} custom={0.55} style={{ transformOrigin: '12px 17px' }} />
    </motion.svg>
  );
}

export function AlertCircle({ className, ...props }) {
  return (
    <motion.svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} initial="hidden" animate="visible" {...props}>
      <motion.circle cx="12" cy="12" r="10" variants={draw} custom={0} />
      <motion.path d="M12 8v4" variants={draw} custom={0.35} />
      <motion.circle cx="12" cy="16" r="0.5" fill="currentColor" stroke="none" variants={pop} custom={0.5} style={{ transformOrigin: '12px 16px' }} />
    </motion.svg>
  );
}

export function ArrowUpRight({ className, ...props }) {
  const v = slide(3, 3);
  return (
    <motion.svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} initial="hidden" animate="visible" {...props}>
      <motion.path d="M7 7h10v10" variants={v} custom={0} />
      <motion.path d="M7 17 17 7" variants={draw} custom={0.15} />
    </motion.svg>
  );
}

export function ArrowDownRight({ className, ...props }) {
  const v = slide(3, -3);
  return (
    <motion.svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} initial="hidden" animate="visible" {...props}>
      <motion.path d="M7 17h10V7" variants={v} custom={0} />
      <motion.path d="M17 17 7 7" variants={draw} custom={0.15} />
    </motion.svg>
  );
}

export function ShoppingCart({ className, ...props }) {
  return (
    <motion.svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} initial="hidden" animate="visible" {...props}>
      <motion.path d="M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12" variants={draw} custom={0} />
      <motion.circle cx="8" cy="21" r="1" variants={pop} custom={0.45} style={{ transformOrigin: '8px 21px' }} />
      <motion.circle cx="19" cy="21" r="1" variants={pop} custom={0.55} style={{ transformOrigin: '19px 21px' }} />
    </motion.svg>
  );
}
