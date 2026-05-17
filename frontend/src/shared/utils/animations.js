const easeOut = [0.16, 1, 0.3, 1];

export const containerVariants = (staggerDelay = 0.03) => ({
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: staggerDelay, delayChildren: 0.04 } },
});

export const cardVariants = {
    hidden: { opacity: 0, y: 10 },
    show: {
        opacity: 1,
        y: 0,
        transition: { duration: 0.32, ease: easeOut },
    },
};
