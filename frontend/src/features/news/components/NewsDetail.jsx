import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useParams } from 'react-router-dom';
import { ArrowLeft, Calendar, ExternalLink, Building2 } from 'lucide-react';
import { newsService } from '../services/newsService';
import { formatDateTimeFull } from '../../../shared/utils/formatters';
import { CategoryBadge } from '../lib/newsConfig.jsx';
import { getFallbackImage } from '../lib/newsConfig';
import LoadingState from '../../../shared/components/feedback/LoadingState';
import ErrorState from '../../../shared/components/feedback/ErrorState';
import useNavigationBack from '../../../shared/hooks/useNavigationBack';

export default function NewsDetail() {
    const { t } = useTranslation();
    const { id } = useParams();
    const goBack = useNavigationBack('/news');
    const [article, setArticle] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchArticle = async () => {
            setLoading(true);
            setError(null);
            try {
                const data = await newsService.getNewsById(id);
                setArticle(data);
            } catch {
                setError(t('newsDetail.error'));
            } finally {
                setLoading(false);
            }
        };
        fetchArticle();
    }, [id, t]);

    if (loading) return <LoadingState message={t('newsDetail.loading')} />;
    if (error) return <ErrorState message={error} onRetry={goBack} />;
    if (!article) return <ErrorState message={t('newsDetail.notFound')} onRetry={goBack} />;

    const imgSrc = article.imageUrl || getFallbackImage(article.category, article.id);
    const hasContent = article.content && article.content.trim().length > 0;

    return (
        <div className="max-w-4xl mx-auto py-6 space-y-6 min-w-0">
            <motion.div
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3 }}
            >
                <button
                    onClick={goBack}
                    className="flex items-center gap-2 text-fg-muted hover:text-fg transition-colors duration-150 text-sm mb-4"
                >
                    <ArrowLeft size={16} />
                    {t('newsDetail.backToList')}
                </button>
            </motion.div>

            <motion.article
                data-tour="news-detail"
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
                className="rounded-xl overflow-hidden bg-bg-elevated card-hover backdrop-blur-md border border-border-default"
            >
                <div className="relative w-full h-56 sm:h-72 md:h-80 overflow-hidden bg-surface">
                    <img
                        src={imgSrc}
                        alt={article.title}
                        className="w-full h-full object-cover"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-bg-elevated via-bg-elevated/20 to-transparent" />
                </div>

                <div className="p-4 sm:p-6 md:p-8 space-y-4 sm:space-y-5 min-w-0">
                    <div className="flex items-center gap-2 sm:gap-3 flex-wrap min-w-0">
                        <CategoryBadge category={article.category} />
                        {article.sourceName && (
                            <div className="flex items-center gap-1.5 text-fg-subtle text-xs min-w-0">
                                <Building2 size={12} strokeWidth={1.6} className="shrink-0" />
                                <span className="truncate max-w-[160px] sm:max-w-none">{article.sourceName}</span>
                            </div>
                        )}
                        <div className="flex items-center gap-1.5 text-fg-subtle text-xs">
                            <Calendar size={12} strokeWidth={1.6} className="shrink-0" />
                            <span>{formatDateTimeFull(article.publishedAt)}</span>
                        </div>
                    </div>

                    <h1 className="text-fg text-xl sm:text-2xl md:text-3xl font-bold leading-tight tracking-[-0.02em] break-words">
                        {article.title}
                    </h1>

                    {article.description && !hasContent && (
                        <p className="text-fg-muted text-sm sm:text-base leading-relaxed break-words">
                            {article.description}
                        </p>
                    )}

                    {hasContent && (
                        <div
                            className="prose prose-invert prose-sm max-w-none text-fg-muted leading-relaxed break-words
                                       prose-headings:text-fg prose-a:text-accent prose-a:no-underline hover:prose-a:underline
                                       prose-img:rounded-lg prose-img:w-full prose-img:h-auto prose-img:max-h-96 prose-img:object-cover
                                       prose-p:mb-4 prose-li:mb-1
                                       prose-pre:overflow-x-auto prose-pre:whitespace-pre"
                            dangerouslySetInnerHTML={{ __html: article.content }}
                        />
                    )}

                    {article.link && (
                        <div className="pt-4 border-t border-border-default">
                            <a
                                href={article.link}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-2 text-accent text-sm font-medium hover:text-accent-bright transition-colors duration-150"
                            >
                                <ExternalLink size={14} strokeWidth={2} />
                                {t('newsDetail.openSource')}{article.sourceName ? ` (${article.sourceName})` : ''}
                            </a>
                        </div>
                    )}
                </div>
            </motion.article>
        </div>
    );
}
