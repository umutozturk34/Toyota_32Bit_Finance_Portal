const easeOut = [0.16, 1, 0.3, 1];

export const containerVariants = (staggerDelay = 0.03) => ({
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
    animate: { opacity: 1, y: 0, transition: { duration: 0.28, ease: easeOut } },
    exit: { opacity: 0, y: -4, transition: { duration: 0.14, ease: 'easeIn' } },
};
