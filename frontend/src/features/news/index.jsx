import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Newspaper, RefreshCw, Clock, SearchX, Tag } from 'lucide-react';
import { newsService } from './newsService';
import { TABS, COOLDOWN_MS } from './newsConfig.jsx';
import NewsFilters from './NewsFilters';
import NewsCard from './NewsCard';
import FeaturedCard from './FeaturedCard';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';

const containerVariants = {
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: 0.045 } },
};

let cachedNews = null;

export default function News() {
    const [allNews, setAllNews] = useState(cachedNews ?? []);
    const [loading, setLoading] = useState(!cachedNews);
    const [error, setError] = useState(null);
    const [activeTab, setActiveTab] = useState(
        () => sessionStorage.getItem('news-active-tab') || 'ALL'
    );
    const [cooldownEnd, setCooldownEnd] = useState(null);
    const [remaining, setRemaining] = useState(0);
    const scrollRef = useRef(null);

    useEffect(() => {
        if (!cachedNews) fetchNews();
        else {
            const saved = sessionStorage.getItem('news-scroll-y');
            if (saved) requestAnimationFrame(() => window.scrollTo(0, parseInt(saved, 10)));
        }
    }, []);

    useEffect(() => {
        if (!cooldownEnd) return;
        const tick = () => {
            const left = Math.max(0, cooldownEnd - Date.now());
            setRemaining(left);
            if (left <= 0) setCooldownEnd(null);
        };
        tick();
        const id = setInterval(tick, 250);
        return () => clearInterval(id);
    }, [cooldownEnd]);

    const isCoolingDown = cooldownEnd && remaining > 0;
    const formatRemaining = (ms) => {
        const s = Math.ceil(ms / 1000);
        return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
    };

    const fetchNews = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await newsService.getLatestNews();
            setAllNews(data);
            cachedNews = data;
        } catch (err) {
            console.error('Error fetching news:', err);
            setError('Haberler yüklenirken bir hata oluştu.');
        } finally {
            setLoading(false);
        }
    }, []);

    const handleRefresh = useCallback(() => {
        if (isCoolingDown) return;
        cachedNews = null;
        fetchNews();
        setCooldownEnd(Date.now() + COOLDOWN_MS);
    }, [isCoolingDown, fetchNews]);

    const handleTabChange = useCallback((tab) => {
        setActiveTab(tab);
        sessionStorage.setItem('news-active-tab', tab);
    }, []);

    const filteredNews = useMemo(() => {
        if (activeTab === 'ALL') return allNews;
        return allNews.filter(a => a.category === activeTab);
    }, [allNews, activeTab]);

    const categoryCounts = useMemo(() => {
        const counts = { ALL: allNews.length };
        TABS.slice(1).forEach(cat => {
            counts[cat] = allNews.filter(a => a.category === cat).length;
        });
        return counts;
    }, [allNews]);

    const featuredArticles = filteredNews.slice(0, 2);
    const restArticles = filteredNews.slice(2);

    if (loading) return <LoadingState message="Haberler yükleniyor…" />;
    if (error) return <ErrorState message={error} onRetry={fetchNews} />;

    return (
        <div className="space-y-6 py-6">
            <motion.div
                initial={{ opacity: 0, y: -14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
                className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
            >
                <div>
                    <h1 className="flex items-center gap-2.5 text-2xl font-bold tracking-[-0.025em] text-fg sm:text-3xl">
                        <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent">
                            <Newspaper size={20} />
                        </span>
                        Finansal Haberler
                    </h1>
                    <p className="text-fg-muted text-sm mt-1 ml-12">
                        Güncel piyasa ve finans haberleri
                        {allNews.length > 0 && (
                            <span className="ml-2 text-fg-subtle">· {allNews.length} haber</span>
                        )}
                    </p>
                </div>

                <button
                    id="news-refresh-btn"
                    onClick={handleRefresh}
                    disabled={loading || isCoolingDown}
                    title={isCoolingDown ? `Cooldown: ${formatRemaining(remaining)}` : 'Yenile'}
                    className="flex items-center gap-2 rounded-md border border-border-default bg-bg-base px-4 py-2 text-sm text-fg-muted transition-colors duration-150 hover:bg-surface hover:text-fg disabled:opacity-50 self-start sm:self-auto"
                >
                    {isCoolingDown ? (
                        <><Clock className="h-4 w-4" />{formatRemaining(remaining)}</>
                    ) : (
                        <><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />Yenile</>
                    )}
                </button>
            </motion.div>

            <NewsFilters
                activeTab={activeTab}
                onTabChange={handleTabChange}
                categoryCounts={categoryCounts}
            />

            {filteredNews.length === 0 ? (
                <motion.div
                    initial={{ opacity: 0, scale: 0.97 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="flex flex-col items-center justify-center gap-3 py-20 rounded-xl border border-border-default bg-bg-elevated card-hover backdrop-blur-md"
                >
                    <div className="flex items-center justify-center w-14 h-14 rounded-xl bg-surface">
                        <SearchX size={28} className="text-fg-subtle" />
                    </div>
                    <p className="text-fg-muted text-sm">Bu kategoride henüz haber bulunmuyor.</p>
                    <p className="text-fg-subtle text-xs">Haberler periyodik olarak güncellenir.</p>
                </motion.div>
            ) : (
                <AnimatePresence mode="wait">
                    <motion.div
                        key={activeTab}
                        variants={containerVariants}
                        initial="hidden"
                        animate="show"
                        className="space-y-4"
                    >
                        {featuredArticles.length > 0 && (
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                {featuredArticles.map((article, i) => (
                                    <FeaturedCard key={article.id ?? i} article={article} index={i} />
                                ))}
                            </div>
                        )}

                        {restArticles.length > 0 && (
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                                {restArticles.map((article, i) => (
                                    <NewsCard key={article.id ?? (i + 2)} article={article} index={i + 2} />
                                ))}
                            </div>
                        )}
                    </motion.div>
                </AnimatePresence>
            )}

            {filteredNews.length > 0 && (
                <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="flex items-center justify-center gap-2 pt-2 text-fg-subtle text-xs"
                >
                    <Tag size={11} strokeWidth={1.6} />
                    <span>{filteredNews.length} haber gösteriliyor</span>
                </motion.div>
            )}
        </div>
    );
}
