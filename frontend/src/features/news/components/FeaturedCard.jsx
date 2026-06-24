import { useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Calendar, ChevronRight } from 'lucide-react';
import { formatDateTimeShort } from '../../../shared/utils/formatters';
import { CategoryBadge } from '../lib/newsConfig.jsx';
import { getFallbackImage } from '../lib/newsConfig';
import AssetMentionTags from './AssetMentionTags';
import Card from '../../../shared/components/card';

const cardVariants = {
    hidden: { opacity: 0, y: 14, scale: 0.98 },
    show: { opacity: 1, y: 0, scale: 1, transition: { duration: 0.28, ease: [0.16, 1, 0.3, 1] } },
};

export default function FeaturedCard({ article, index }) {
    const navigate = useNavigate();
    const { t } = useTranslation();
    const fallbackSrc = getFallbackImage(article.category, article.id ?? index);
    const [imgSrc, setImgSrc] = useState(article.imageUrl || fallbackSrc);
    const [imgLoaded, setImgLoaded] = useState(false);

    const handleClick = () => {
        if (article.id) navigate(`/news/${article.id}`);
    };

    const handleImageError = () => {
        if (imgSrc !== fallbackSrc) {
            setImgSrc(fallbackSrc);
            setImgLoaded(false);
        } else {
            setImgLoaded(true);
        }
    };

    return (
        <Card
            as={motion.article}
            variants={cardVariants}
            onClick={handleClick}
            interactive
            radius="xl"
            padding="none"
            className="group flex flex-col"
            aria-label={article.title}
        >
            <div className="relative w-full h-56 sm:h-64 overflow-hidden bg-surface">
                <img
                    src={imgSrc}
                    alt={article.title}
                    loading="lazy"
                    className={`w-full h-full object-cover transition-all duration-700 group-hover:scale-[1.04] ${imgLoaded ? 'opacity-100' : 'opacity-0'}`}
                    onLoad={() => setImgLoaded(true)}
                    onError={handleImageError}
                />
                <div className="absolute inset-0 bg-gradient-to-t from-bg-elevated via-bg-elevated/20 to-transparent" />

                <div className="absolute bottom-0 left-0 right-0 p-4 sm:p-5 min-w-0">
                    <div className="mb-2">
                        <CategoryBadge category={article.category} overlay />
                    </div>
                    <h3 className="text-fg text-base sm:text-lg font-bold leading-snug line-clamp-2 break-words group-hover:text-accent-bright transition-colors duration-150 drop-shadow-lg">
                        {article.title}
                    </h3>
                    <div className="flex items-center gap-x-3 gap-y-1 mt-2 flex-wrap min-w-0">
                        <span className="text-accent text-[11px] font-semibold uppercase tracking-widest truncate max-w-full">
                            {article.sourceName}
                        </span>
                        <span className="text-fg-subtle text-[11px] flex items-center gap-1 shrink-0">
                            <Calendar size={10} strokeWidth={1.6} />
                            {formatDateTimeShort(article.publishedAt)}
                        </span>
                        <span className="sm:ml-auto text-accent text-[11px] font-medium opacity-0 group-hover:opacity-100 transition-opacity duration-150 hidden sm:flex items-center gap-1">
                            <ChevronRight size={11} strokeWidth={2} />
                            {t('news.readMore')}
                        </span>
                    </div>
                    {Array.isArray(article.assets) && article.assets.length > 0 && (
                        <div className="mt-2" onClick={(e) => e.stopPropagation()}>
                            <AssetMentionTags assets={article.assets} date={article.publishedAt} limit={4} />
                        </div>
                    )}
                </div>
            </div>
        </Card>
    );
}
