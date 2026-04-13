import { useQuery } from '@tanstack/react-query';
import { Newspaper } from 'lucide-react';
import { newsService } from './newsService';
import { CATEGORY_CONFIG } from './newsConfig.jsx';
import FilterTabs from '../../shared/components/FilterTabs';

export default function NewsFilters({ activeTab, onTabChange }) {
    const { data: categoryCounts = [] } = useQuery({
        queryKey: ['newsCategories'],
        queryFn: newsService.getCategories,
        staleTime: 60_000,
    });

    const items = categoryCounts.map(c => {
        const cfg = CATEGORY_CONFIG[c.type] || {};
        return { type: c.type, count: c.count, label: cfg.label || c.type };
    });

    const totalCount = categoryCounts.reduce((sum, c) => sum + Number(c.count), 0);

    return (
        <FilterTabs
            items={items}
            activeId={activeTab}
            onSelect={onTabChange}
            allCount={totalCount}
            layoutId="news-category"
        />
    );
}
