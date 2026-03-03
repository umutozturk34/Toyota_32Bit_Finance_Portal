export const containerVariants = (staggerDelay = 0.05) => ({
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: staggerDelay } },
});

export const cardVariants = {
    hidden: { opacity: 0, y: 24 },
    show: {
        opacity: 1,
        y: 0,
        transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] },
    },
};
