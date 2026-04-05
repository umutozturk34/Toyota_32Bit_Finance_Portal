import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Calendar, ChevronRight } from 'lucide-react';
import { formatDateTimeShort } from '../../shared/utils/formatters';
import { getFallbackImage, CategoryBadge } from './newsConfig.jsx';

const cardVariants = {
    hidden: { opacity: 0, y: 14, scale: 0.98 },
    show: { opacity: 1, y: 0, scale: 1, transition: { duration: 0.28, ease: [0.16, 1, 0.3, 1] } },
};

export default function NewsCard({ article, index }) {
    const navigate = useNavigate();
    const imgSrc = article.imageUrl || getFallbackImage(article.category, article.id ?? index);
    const [imgLoaded, setImgLoaded] = useState(false);

    const handleClick = () => {
        if (article.id) {
            sessionStorage.setItem('news-scroll-y', String(window.scrollY));
            navigate(`/news/${article.id}`);
        }
    };

    return (
        <motion.article
            variants={cardVariants}
            onClick={handleClick}
            className="group relative flex flex-col rounded-xl overflow-hidden cursor-pointer bg-bg-elevated border border-border-default hover:border-border-hover card-hover transition-all duration-200"
            aria-label={article.title}
        >
            <div className="relative w-full h-44 overflow-hidden bg-surface">
                <img
                    src={imgSrc}
                    alt={article.title}
                    className={`w-full h-full object-cover transition-all duration-500 group-hover:scale-105 ${imgLoaded ? 'opacity-100' : 'opacity-0'}`}
                    onLoad={() => setImgLoaded(true)}
                />
                <div className="absolute inset-0 bg-gradient-to-t from-bg-elevated/80 via-transparent to-transparent" />
                <div className="absolute bottom-2.5 left-3">
                    <CategoryBadge category={article.category} />
                </div>
            </div>

            <div className="flex flex-col flex-1 p-4 gap-2.5">
                <span className="text-accent text-[11px] font-semibold uppercase tracking-widest leading-none">
                    {article.sourceName}
                </span>

                <h3 className="text-fg text-[14px] font-semibold leading-snug line-clamp-2 group-hover:text-accent-bright transition-colors duration-150">
                    {article.title}
                </h3>

                {article.description && (
                    <p className="text-fg-muted text-xs leading-relaxed line-clamp-2">
                        {article.description.length > 120
                            ? article.description.substring(0, 120) + '\u2026'
                            : article.description}
                    </p>
                )}

                <div className="flex-1" />

                <div className="flex items-center justify-between pt-2.5 border-t border-border-default">
                    <div className="flex items-center gap-1.5 text-fg-subtle text-[11px]">
                        <Calendar size={11} strokeWidth={1.6} />
                        <span>{formatDateTimeShort(article.publishedAt)}</span>
                    </div>
                    <div className="flex items-center gap-1.5 text-accent text-[11px] font-medium opacity-0 group-hover:opacity-100 transition-opacity duration-150">
                        <ChevronRight size={11} strokeWidth={2} />
                        <span>Devamini Oku</span>
                    </div>
                </div>
            </div>
        </motion.article>
    );
}
