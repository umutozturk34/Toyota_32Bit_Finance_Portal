import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, Calendar, ExternalLink, Building2 } from 'lucide-react';
import { newsService } from './newsService';
import { formatDateTimeFull } from '../../shared/utils/formatters';
import { CategoryBadge, getFallbackImage } from './newsConfig.jsx';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';

export default function NewsDetail() {
    const { id } = useParams();
    const navigate = useNavigate();
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
            } catch (err) {
                setError('Haber yuklenirken bir hata olustu.');
            } finally {
                setLoading(false);
            }
        };
        fetchArticle();
    }, [id]);

    if (loading) return <LoadingState message="Haber yukleniyor..." />;
    if (error) return <ErrorState message={error} onRetry={() => navigate('/news')} />;
    if (!article) return <ErrorState message="Haber bulunamadi." onRetry={() => navigate('/news')} />;

    const imgSrc = article.imageUrl || getFallbackImage(article.category, article.id);
    const hasContent = article.content && article.content.trim().length > 0;

    return (
        <div className="max-w-4xl mx-auto py-6 space-y-6">
            <motion.div
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3 }}
            >
                <button
                    onClick={() => navigate(-1)}
                    className="flex items-center gap-2 text-fg-muted hover:text-fg transition-colors duration-150 text-sm mb-4"
                >
                    <ArrowLeft size={16} />
                    Haberlere Don
                </button>
            </motion.div>

            <motion.article
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
                className="rounded-xl overflow-hidden bg-bg-elevated card-hover backdrop-blur-md border border-border-default"
            >
                <div className="relative w-full h-64 sm:h-80 overflow-hidden bg-surface">
                    <img
                        src={imgSrc}
                        alt={article.title}
                        className="w-full h-full object-cover"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-bg-elevated via-bg-elevated/20 to-transparent" />
                </div>

                <div className="p-6 sm:p-8 space-y-5">
                    <div className="flex items-center gap-3 flex-wrap">
                        <CategoryBadge category={article.category} />
                        {article.sourceName && (
                            <div className="flex items-center gap-1.5 text-fg-subtle text-xs">
                                <Building2 size={12} strokeWidth={1.6} />
                                <span>{article.sourceName}</span>
                            </div>
                        )}
                        <div className="flex items-center gap-1.5 text-fg-subtle text-xs">
                            <Calendar size={12} strokeWidth={1.6} />
                            <span>{formatDateTimeFull(article.publishedAt)}</span>
                        </div>
                    </div>

                    <h1 className="text-fg text-2xl sm:text-3xl font-bold leading-tight tracking-[-0.02em]">
                        {article.title}
                    </h1>

                    {article.description && !hasContent && (
                        <p className="text-fg-muted text-base leading-relaxed">
                            {article.description}
                        </p>
                    )}

                    {hasContent && (
                        <div
                            className="prose prose-invert prose-sm max-w-none text-fg-muted leading-relaxed
                                       prose-headings:text-fg prose-a:text-accent prose-a:no-underline hover:prose-a:underline
                                       prose-img:rounded-lg prose-img:max-h-96 prose-img:object-cover
                                       prose-p:mb-4 prose-li:mb-1"
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
                                Kaynaga Git{article.sourceName ? ` (${article.sourceName})` : ''}
                            </a>
                        </div>
                    )}
                </div>
            </motion.article>
        </div>
    );
}
