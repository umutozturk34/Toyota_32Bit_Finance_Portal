const easeOut = [0.16, 1, 0.3, 1];

export const containerVariants = (staggerDelay = 0.05) => ({
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: staggerDelay, delayChildren: 0.1 } },
});

export const cardVariants = {
    hidden: { opacity: 0, y: 28 },
    show: {
        opacity: 1,
        y: 0,
        transition: { duration: 0.7, ease: easeOut },
    },
};
