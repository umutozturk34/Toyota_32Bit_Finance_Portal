import React, { useState, useEffect, useMemo } from 'react';
import { newsService } from '../services/dataService';
import './News.css';

const News = () => {
    const [allNews, setAllNews] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [category, setCategory] = useState('all');

    const categoryRules = [
        {
            name: 'CRYPTO',
            keywords: ['bitcoin', 'btc', 'ethereum', 'eth', 'crypto', 'cryptocurrency', 'blockchain', 
                      'dogecoin', 'ripple', 'xrp', 'binance', 'coinbase', 'solana', 'cardano',
                      'defi', 'nft', 'altcoin', 'stablecoin', 'usdt', 'usdc', 'crypto mining',
                      'liquidation', 'memecoin', 'shiba', 'pepe coin', 'litecoin', 'polkadot',
                      'avalanche', 'chainlink', 'uniswap', 'aave']
        },
        {
            name: 'FOREX_METALS',
            keywords: ['forex', 'gold futures', 'gold price', 'silver price', 'precious metal', 'platinum',
                      'currency', 'exchange rate', 'tariff', 'trade war', 'trade deal',
                      'copper', 'oil price', 'crude oil', 'commodity', 'commodities',
                      'döviz', 'altın', 'gümüş', 'petrol', 'inflation data', 'cpi',
                      'canada tariff', 'mexico tariff', 'china tariff', 'import duty']
        },
        {
            name: 'ISTANBUL_STOCK',
            keywords: ['borsa istanbul', 'bist', 'xu100', 'istanbul stock', 'turkish stock',
                      'aselsan', 'thyao', 'turkish airlines', 'garanti', 'akbank', 'türkiye',
                      'koç', 'sabancı', 'halkbank', 'yapı kredi', 'eregli', 'tupras', 
                      'arcelik', 'bim', 'migros', 'gyo', 'turkish economy', 'turkey inflation',
                      'turkey stock', 'turkey economy', 'turkish lira']
        },
        {
            name: 'US_STOCK',
            keywords: ['wall street', 'nasdaq', 'dow jones', 's&p 500', 'nyse', 
                      'aapl', 'apple inc', 'msft', 'microsoft', 'googl', 'alphabet',
                      'tesla', 'tsla', 'amazon', 'amzn', 'meta platforms', 'nvidia', 'nvda',
                      'amd', 'intel', 'netflix', 'jpmorgan', 'goldman sachs', 'berkshire',
                      'fed chair', 'federal reserve', 'earnings report', 'stock rally']
        }
    ];

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

    const getArticleCategory = (article) => {
        const text = `${article.title || ''} ${article.description || ''}`.toLowerCase();
        for (const rule of categoryRules) {
            if (rule.keywords.some(keyword => text.includes(keyword.toLowerCase()))) {
                return rule.name;
            }
        }
        return 'GENERAL';
    };

    const filteredNews = useMemo(() => {
        if (category === 'all') {
            return allNews.slice(0, 25);
        }
        return allNews.filter(article => getArticleCategory(article) === category);
    }, [allNews, category]);

    const formatDate = (dateString) => {
        const date = new Date(dateString);
        return date.toLocaleDateString('tr-TR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    };

    const openArticle = (url) => {
        window.open(url, '_blank', 'noopener,noreferrer');
    };

    if (loading) {
        return (
            <div className="news-container">
                <div className="news-loading">📰 Loading news...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="news-container">
                <div className="news-error">❌ {error}</div>
            </div>
        );
    }

    return (
        <div className="news-container">
            <div className="news-header">
                <h1>📰 Financial News</h1>
                <p>Latest business and stock market news</p>
            </div>

            <div className="news-categories">
                <button 
                    className={category === 'all' ? 'active' : ''}
                    onClick={() => setCategory('all')}
                >
                    🌐 Tümü (25)
                </button>
                <button 
                    className={category === 'CRYPTO' ? 'active' : ''}
                    onClick={() => setCategory('CRYPTO')}
                >
                    ₿ Kripto
                </button>
                <button 
                    className={category === 'ISTANBUL_STOCK' ? 'active' : ''}
                    onClick={() => setCategory('ISTANBUL_STOCK')}
                >
                    🇹🇷 Borsa Istanbul
                </button>
                <button 
                    className={category === 'FOREX_METALS' ? 'active' : ''}
                    onClick={() => setCategory('FOREX_METALS')}
                >
                    💰 Döviz & Madenler
                </button>
                <button 
                    className={category === 'US_STOCK' ? 'active' : ''}
                    onClick={() => setCategory('US_STOCK')}
                >
                    🇺🇸 ABD Borsası
                </button>
            </div>

            {filteredNews.length === 0 ? (
                <div className="news-empty">Bu kategoride haber bulunamadı.</div>
            ) : (
                <>
                    <div className="news-count">
                        {category !== 'all' && `${filteredNews.length} haber bulundu`}
                    </div>
                    <div className="news-grid">
                        {filteredNews.map((article) => (
                            <div
                                key={article.id}
                                className="news-card"
                                onClick={() => openArticle(article.url)}
                            >
                                {article.imageUrl && (
                                    <img
                                        src={article.imageUrl}
                                        alt={article.title}
                                        className="news-card-image"
                                        onError={(e) => {
                                            e.target.style.display = 'none';
                                        }}
                                    />
                                )}
                                <div className="news-card-content">
                                    <div className="news-card-source">{article.source}</div>
                                    <h3 className="news-card-title">{article.title}</h3>
                                    {article.description && (
                                        <p className="news-card-description">
                                            {article.description.substring(0, 150)}
                                            {article.description.length > 150 && '...'}
                                        </p>
                                    )}
                                    <div className="news-card-footer">
                                        <span>{formatDate(article.publishedAt)}</span>
                                        {article.author && <span>by {article.author}</span>}
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </>
            )}
        </div>
    );
};

export default News;
