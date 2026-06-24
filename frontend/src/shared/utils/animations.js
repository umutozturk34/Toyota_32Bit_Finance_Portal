// The single source of truth for the app's motion language. Import these instead of hand-rolling inline
// easings/durations/springs so every transition shares one feel.

/** Easing curves. `standard` = the calm settle used for most entrances; `emphasized` = a touch snappier;
 *  `drawer` = the decisive slide for panels/drawers. */
export const EASE = {
    standard: [0.16, 1, 0.3, 1],
    emphasized: [0.22, 1, 0.36, 1],
    drawer: [0.32, 0.72, 0, 1],
};

/** Durations (seconds). */
export const DURATION = {
    fast: 0.16,
    base: 0.28,
    slow: 0.42,
};

/** Springs. `press` = button/chip tap; `tab` = the sliding active-tab pill; `panel` = a settling panel/spotlight. */
export const SPRING = {
    press: { type: 'spring', stiffness: 420, damping: 24, mass: 0.6 },
    tab: { type: 'spring', stiffness: 300, damping: 30 },
    panel: { type: 'spring', stiffness: 260, damping: 30 },
};

/** Default stagger between children in a list/grid reveal. */
export const STAGGER = 0.04;

const easeOut = EASE.standard;

export const containerVariants = (staggerDelay = STAGGER) => ({
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: staggerDelay, delayChildren: 0.04 } },
});

export const cardVariants = {
    hidden: { opacity: 0, y: 4 },
    show: {
        opacity: 1,
        y: 0,
        transition: { duration: 0.32, ease: easeOut },
    },
};

// A light "landing" for content that swaps in place — a tab panel, a re-sorted/filtered list. Lifts up on enter,
// drifts down a touch on exit, so a tab or sort change reads as a deliberate beat rather than an instant repaint.
export const landingVariants = {
    initial: { opacity: 0, y: 8 },
    animate: { opacity: 1, y: 0, transition: { duration: 0.26, ease: easeOut } },
    exit: { opacity: 0, y: -4, transition: { duration: 0.1, ease: 'easeIn' } },
};

// A page/panel entrance — a cross-fade with a small lift. Used by the route-level <PageTransition> and any panel
// that should land on mount (incl. F5).
export const panelVariants = {
    initial: { opacity: 0, y: 12 },
    animate: { opacity: 1, y: 0, transition: { duration: DURATION.base, ease: EASE.emphasized } },
    exit: { opacity: 0, y: -6, transition: { duration: DURATION.fast, ease: 'easeIn' } },
};

// A single row/cell inside a staggered list (pair with containerVariants on the parent).
export const listItemVariants = {
    hidden: { opacity: 0, y: 6 },
    show: { opacity: 1, y: 0, transition: { duration: 0.26, ease: easeOut } },
};
