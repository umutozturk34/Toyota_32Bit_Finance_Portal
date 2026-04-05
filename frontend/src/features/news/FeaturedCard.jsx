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

export default function FeaturedCard({ article, index }) {
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
            <div className="relative w-full h-64 overflow-hidden bg-surface">
                <img
                    src={imgSrc}
                    alt={article.title}
                    className={`w-full h-full object-cover transition-all duration-700 group-hover:scale-[1.04] ${imgLoaded ? 'opacity-100' : 'opacity-0'}`}
                    onLoad={() => setImgLoaded(true)}
                />
                <div className="absolute inset-0 bg-gradient-to-t from-bg-elevated via-bg-elevated/20 to-transparent" />

                <div className="absolute bottom-0 left-0 right-0 p-5">
                    <div className="mb-2">
                        <CategoryBadge category={article.category} />
                    </div>
                    <h3 className="text-fg text-lg font-bold leading-snug line-clamp-2 group-hover:text-accent-bright transition-colors duration-150 drop-shadow-lg">
                        {article.title}
                    </h3>
                    <div className="flex items-center gap-3 mt-2 flex-wrap">
                        <span className="text-accent text-[11px] font-semibold uppercase tracking-widest">
                            {article.sourceName}
                        </span>
                        <span className="text-fg-subtle text-[11px] flex items-center gap-1">
                            <Calendar size={10} strokeWidth={1.6} />
                            {formatDateTimeShort(article.publishedAt)}
                        </span>
                        <span className="ml-auto text-accent text-[11px] font-medium opacity-0 group-hover:opacity-100 transition-opacity duration-150 flex items-center gap-1">
                            <ChevronRight size={11} strokeWidth={2} />
                            Devamini Oku
                        </span>
                    </div>
                </div>
            </div>
        </motion.article>
    );
}
