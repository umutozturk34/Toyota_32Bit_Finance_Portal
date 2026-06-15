import { useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Calendar, ChevronRight } from 'lucide-react';
import { formatDateTimeShort } from '../../../shared/utils/formatters';
import { CategoryBadge } from '../lib/newsConfig.jsx';
import { getFallbackImage } from '../lib/newsConfig';
import AssetMentionTag from './AssetMentionTag';
import Card from '../../../shared/components/card';

const cardVariants = {
    hidden: { opacity: 0, y: 14, scale: 0.98 },
    show: { opacity: 1, y: 0, scale: 1, transition: { duration: 0.28, ease: [0.16, 1, 0.3, 1] } },
};

export default function NewsCard({ article, index }) {
    const navigate = useNavigate();
    const { t } = useTranslation();
    const fallbackSrc = getFallbackImage(article.category, article.id ?? index);
    const [imgSrc, setImgSrc] = useState(article.imageUrl || fallbackSrc);
    const [imgLoaded, setImgLoaded] = useState(false);
    // The assets this article mentions (stocks + crypto) are resolved server-side and arrive on the article.
    const mentions = Array.isArray(article.assets) ? article.assets : [];

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
            <div className="relative w-full h-40 sm:h-44 overflow-hidden bg-surface">
                <img
                    src={imgSrc}
                    alt={article.title}
                    loading="lazy"
                    className={`w-full h-full object-cover transition-all duration-500 group-hover:scale-105 ${imgLoaded ? 'opacity-100' : 'opacity-0'}`}
                    onLoad={() => setImgLoaded(true)}
                    onError={handleImageError}
                />
                <div className="absolute inset-0 bg-gradient-to-t from-bg-elevated/80 via-transparent to-transparent" />
                <div className="absolute bottom-2.5 left-3 max-w-[calc(100%-1.5rem)]">
                    <CategoryBadge category={article.category} />
                </div>
            </div>

            <div className="flex flex-col flex-1 p-3.5 sm:p-4 gap-2 sm:gap-2.5 min-w-0">
                <span className="text-accent text-[11px] font-semibold uppercase tracking-widest leading-none truncate">
                    {article.sourceName}
                </span>

                <h3 className="text-fg text-[14px] font-semibold leading-snug line-clamp-2 break-words group-hover:text-accent-bright transition-colors duration-150">
                    {article.title}
                </h3>

                {mentions.length > 0 && (
                    <div className="flex flex-wrap items-center gap-1.5">
                        {mentions.map((a) => (
                            <AssetMentionTag key={`${a.type}:${a.code}`} code={a.code} type={a.type} />
                        ))}
                    </div>
                )}

                {article.description && (
                    <p className="text-fg-muted text-xs leading-relaxed line-clamp-2 break-words">
                        {article.description.length > 120
                            ? article.description.substring(0, 120) + '…'
                            : article.description}
                    </p>
                )}

                <div className="flex-1" />

                <div className="flex items-center justify-between gap-2 pt-2.5 border-t border-border-default min-w-0">
                    <div className="flex items-center gap-1.5 text-fg-subtle text-[11px] min-w-0">
                        <Calendar size={11} strokeWidth={1.6} className="shrink-0" />
                        <span className="truncate">{formatDateTimeShort(article.publishedAt)}</span>
                    </div>
                    <div className="flex items-center gap-1.5 text-accent text-[11px] font-medium opacity-0 group-hover:opacity-100 transition-opacity duration-150">
                        <ChevronRight size={11} strokeWidth={2} />
                        <span>{t('news.readMore')}</span>
                    </div>
                </div>
            </div>
        </Card>
    );
}
