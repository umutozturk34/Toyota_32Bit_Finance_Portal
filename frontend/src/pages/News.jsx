import { useState, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
    Globe, Bitcoin, Landmark, Coins, Flag,
    Newspaper, ExternalLink, Calendar, User, SearchX
} from 'lucide-react';
import { useTheme } from '../context/ThemeContext';
import { newsService } from '../services/dataService';
import { filterNewsByCategory } from '../utils/newsCategories';
import { formatDateLong } from '../utils/formatters';
import LoadingState from '../components/LoadingState';
import ErrorState from '../components/ErrorState';
const categories = [
    { key: 'all', label: 'Tümü (25)', icon: Globe },
    { key: 'CRYPTO', label: 'Kripto', icon: Bitcoin },
    { key: 'ISTANBUL_STOCK', label: 'Borsa Istanbul', icon: Landmark },
    { key: 'FOREX_METALS', label: 'Döviz & Madenler', icon: Coins },
    { key: 'US_STOCK', label: 'ABD Borsası', icon: Flag },
];
const container = {
    hidden: { opacity: 0 },
    show: {
        opacity: 1,
        transition: { staggerChildren: 0.04 },
    },
};
const item = {
    hidden: { opacity: 0, y: 10 },
    show: { opacity: 1, y: 0, transition: { duration: 0.25, ease: 'easeOut' } },
};
const News = () => {
    const { isDark } = useTheme();
    const [allNews, setAllNews] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [category, setCategory] = useState('all');
    useEffect(() => {
        fetchAllNews();
    }, []);
    const fetchAllNews = async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await newsService.getAllNews(0, 50);
            if (response.success && response.data) {
                setAllNews(response.data.content || []);
            } else {
                setError('Failed to load news');
            }
        } catch (err) {
            console.error('Error fetching news:', err);
            setError('Failed to load news. Please try again later.');
        } finally {
            setLoading(false);
        }
    };
    const filteredNews = useMemo(() => {
        return filterNewsByCategory(allNews, category);
    }, [allNews, category]);
    const openArticle = (url) => {
        window.open(url, '_blank', 'noopener,noreferrer');
    };
    if (loading) return <LoadingState message="Loading news..." />;
    if (error) return <ErrorState message={error} onRetry={fetchAllNews} />;
    return (
        <div className="py-6">
            {}
            <motion.div
                initial={{ opacity: 0, y: -12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
                className="mb-8"
            >
                <h1 className="group flex items-center gap-3 text-2xl md:text-3xl font-bold tracking-[-0.025em] text-fg">
                    <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent">
                        <Newspaper size={20} />
                    </span>
                    Financial News
                </h1>
                <p className="text-fg-muted text-sm mt-1.5 ml-12">
                    Latest business and stock market news
                </p>
            </motion.div>
            {}
            <motion.div
                initial={{ opacity: 0, y: -6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.25, delay: 0.08 }}
                className="flex flex-wrap gap-2 mb-6 p-1.5 rounded-xl border border-border-default bg-bg-elevated"
            >
                {categories.map(({ key, label, icon: Icon }) => (
                    <button
                        key={key}
                        onClick={() => setCategory(key)}
                        className={`
                            flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-sm font-medium
                            transition-all duration-150 cursor-pointer border-none
                            ${category === key
                                ? 'bg-accent text-white shadow-[0_1px_3px_rgba(94,106,210,0.4)]'
                                : 'bg-transparent text-fg-muted hover:text-fg hover:bg-surface'
                            }
                        `}
                    >
                        <Icon size={15} strokeWidth={1.8} className={category === key ? 'text-white' : 'text-fg-subtle'} />
                        {label}
                    </button>
                ))}
            </motion.div>
            {}
            {category !== 'all' && (
                <motion.p
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="text-fg-subtle text-sm mb-4"
                >
                    {filteredNews.length} haber bulundu
                </motion.p>
            )}
            {}
            {filteredNews.length === 0 ? (
                <motion.div
                    initial={{ opacity: 0, scale: 0.98 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="flex flex-col items-center justify-center gap-3 py-16 rounded-lg border border-border-default bg-bg-base"
                >
                    <SearchX size={36} className="text-fg-subtle" />
                    <p className="text-fg-muted text-sm">Bu kategoride haber bulunamadı.</p>
                </motion.div>
            ) : (
                                <AnimatePresence mode="wait">
                    <motion.div
                        key={category}
                        variants={container}
                        initial="hidden"
                        animate="show"
                        className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
                    >
                        {filteredNews.map((article) => (
                            <motion.div
                                key={article.id}
                                variants={item}
                                onClick={() => openArticle(article.url)}
                                className="group relative flex flex-col rounded-xl overflow-hidden cursor-pointer bg-bg-elevated border border-border-default hover:border-border-hover card-hover transition-all duration-200"
                            >
                                {}
                                {article.imageUrl && (
                                    <div className="relative w-full h-40 overflow-hidden bg-surface">
                                        <img
                                            src={article.imageUrl}
                                            alt={article.title}
                                            className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                                            onError={(e) => { e.target.style.display = 'none'; }}
                                        />
                                    </div>
                                )}
                                {}
                                <div className="flex flex-col flex-1 p-4 gap-2">
                                    {}
                                    <span className="text-accent text-xs font-semibold uppercase tracking-wider">
                                        {article.source}
                                    </span>
                                    {}
                                    <h3 className="text-fg text-[15px] font-semibold leading-snug line-clamp-2 group-hover:text-accent-bright transition-colors duration-150">
                                        {article.title}
                                    </h3>
                                    {}
                                    {article.description && (
                                        <p className="text-fg-muted text-xs leading-relaxed line-clamp-3">
                                            {article.description.substring(0, 150)}
                                            {article.description.length > 150 && '...'}
                                        </p>
                                    )}
                                    {}
                                    <div className="flex-1" />
                                    {}
                                    <div className="flex items-center justify-between pt-2.5 border-t border-border-default text-fg-subtle text-xs">
                                        <div className="flex items-center gap-1.5">
                                            <Calendar size={12} strokeWidth={1.6} />
                                            <span>{formatDateLong(article.publishedAt)}</span>
                                        </div>
                                        {article.author && (
                                            <div className="flex items-center gap-1.5 truncate max-w-[45%]">
                                                <User size={12} strokeWidth={1.6} />
                                                <span className="truncate">{article.author}</span>
                                            </div>
                                        )}
                                    </div>
                                    {}
                                    <div className="flex items-center gap-1.5 text-accent text-xs font-medium opacity-0 group-hover:opacity-100 transition-opacity duration-150 mt-0.5">
                                        <ExternalLink size={12} strokeWidth={1.8} />
                                        <span>Read article</span>
                                    </div>
                                </div>
                            </motion.div>
                        ))}
                    </motion.div>
                </AnimatePresence>
            )}
        </div>
    );
};
export default News;
