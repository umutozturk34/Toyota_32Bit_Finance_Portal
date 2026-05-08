import { useState, useEffect, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import { Newspaper, Clock, SearchX, Tag } from 'lucide-react';
import { RefreshCw } from '../../shared/components/feedback/AnimatedIcons';
import { newsService } from './services/newsService';
import { TABS, COOLDOWN_MS } from './lib/newsConfig.jsx';
import NewsFilters from './components/NewsFilters';
import NewsCard from './components/NewsCard';
import FeaturedCard from './components/FeaturedCard';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import SearchInput from '../../shared/components/form/SearchInput';
import Pagination from '../../shared/components/form/Pagination';
import useAppStore from '../../shared/stores/useAppStore';
import useListParams from '../../shared/hooks/useListParams';

const containerVariants = {
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: 0.045 } },
};

export default function News() {
    const setCooldown = useAppStore((s) => s.setCooldown);
    const cooldownEnd = useAppStore((s) => s.getCooldownEnd('/news'));
    const [remaining, setRemaining] = useState(() => Math.max(0, cooldownEnd - Date.now()));
    const listParams = useListParams({ defaultSize: 9 });
    const activeTab = listParams.filter || 'ALL';

    const queryParams = {
        ...listParams.params,
        ...(activeTab !== 'ALL' && { category: activeTab }),
    };

    const { data, isLoading: loading, error, refetch } = useQuery({
        queryKey: ['news', activeTab, listParams.params],
        queryFn: () => newsService.search(queryParams),
        placeholderData: (prev) => prev,
    });

    const articles = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;
    const totalCount = data?.totalElements ?? 0;

    useEffect(() => {
        if (!cooldownEnd || cooldownEnd <= Date.now()) return;
        const tick = () => setRemaining(Math.max(0, cooldownEnd - Date.now()));
        tick();
        const id = setInterval(tick, 250);
        return () => clearInterval(id);
    }, [cooldownEnd]);

    const isCoolingDown = cooldownEnd && remaining > 0;
    const formatRemaining = (ms) => {
        const s = Math.ceil(ms / 1000);
        return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
    };

    const handleRefresh = useCallback(() => {
        if (isCoolingDown) return;
        refetch();
        setCooldown('/news', Date.now() + COOLDOWN_MS);
    }, [isCoolingDown, refetch, setCooldown]);

    const handleTabChange = useCallback((tab) => {
        listParams.setFilter(tab);
    }, [listParams]);

    const isFirstPage = listParams.page === 0;
    const featuredArticles = isFirstPage ? articles.slice(0, 2) : [];
    const restArticles = isFirstPage ? articles.slice(2) : articles;

    if (loading && articles.length === 0) return <LoadingState message="Haberler yükleniyor…" />;
    if (error) return <ErrorState message="Haberler yüklenirken bir hata oluştu" onRetry={refetch} />;

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
                        {totalCount > 0 && (
                            <span className="ml-2 text-fg-subtle">· {totalCount} haber</span>
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

            <div className="flex flex-wrap items-center gap-3">
                <div className="w-48">
                    <SearchInput value={listParams.search} onChange={listParams.setSearch} placeholder="Haber ara..." />
                </div>
            </div>

            <NewsFilters
                activeTab={activeTab}
                onTabChange={handleTabChange}
            />

            {articles.length === 0 ? (
                <motion.div
                    initial={{ opacity: 0, scale: 0.97 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="flex flex-col items-center justify-center gap-3 py-20 rounded-xl border border-border-default bg-bg-elevated card-hover backdrop-blur-md"
                >
                    <div className="flex items-center justify-center w-14 h-14 rounded-xl bg-surface">
                        <SearchX size={28} className="text-fg-subtle" />
                    </div>
                    <p className="text-fg-muted text-sm">
                        {listParams.search ? 'Aramayla eşleşen haber bulunamadı.' : 'Bu kategoride henüz haber bulunmuyor.'}
                    </p>
                    <p className="text-fg-subtle text-xs">Haberler periyodik olarak güncellenir.</p>
                </motion.div>
            ) : (
                <AnimatePresence mode="wait">
                    <motion.div
                        key={`${activeTab}-${listParams.page}`}
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
                                    <NewsCard key={article.id ?? (i + featuredArticles.length)} article={article} index={i + featuredArticles.length} />
                                ))}
                            </div>
                        )}
                    </motion.div>
                </AnimatePresence>
            )}

            <Pagination page={listParams.page} totalPages={totalPages} onPageChange={listParams.setPage} />
        </div>
    );
}
