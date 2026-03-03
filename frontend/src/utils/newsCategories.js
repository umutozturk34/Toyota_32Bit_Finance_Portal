const categoryRules = [
    {
        name: 'CRYPTO',
        keywords: ['bitcoin', 'btc', 'ethereum', 'eth', 'crypto', 'cryptocurrency', 'blockchain',
            'dogecoin', 'ripple', 'xrp', 'binance', 'coinbase', 'solana', 'cardano',
            'defi', 'nft', 'altcoin', 'stablecoin', 'usdt', 'usdc', 'crypto mining',
            'liquidation', 'memecoin', 'shiba', 'pepe coin', 'litecoin', 'polkadot',
            'avalanche', 'chainlink', 'uniswap', 'aave'],
    },
    {
        name: 'FOREX_METALS',
        keywords: ['forex', 'gold futures', 'gold price', 'silver price', 'precious metal', 'platinum',
            'currency', 'exchange rate', 'tariff', 'trade war', 'trade deal',
            'copper', 'oil price', 'crude oil', 'commodity', 'commodities',
            'döviz', 'altın', 'gümüş', 'petrol', 'inflation data', 'cpi',
            'canada tariff', 'mexico tariff', 'china tariff', 'import duty'],
    },
    {
        name: 'ISTANBUL_STOCK',
        keywords: ['borsa istanbul', 'bist', 'xu100', 'istanbul stock', 'turkish stock',
            'aselsan', 'thyao', 'turkish airlines', 'garanti', 'akbank', 'türkiye',
            'koç', 'sabancı', 'halkbank', 'yapı kredi', 'eregli', 'tupras',
            'arcelik', 'bim', 'migros', 'gyo', 'turkish economy', 'turkey inflation',
            'turkey stock', 'turkey economy', 'turkish lira'],
    },
    {
        name: 'US_STOCK',
        keywords: ['wall street', 'nasdaq', 'dow jones', 's&p 500', 'nyse',
            'aapl', 'apple inc', 'msft', 'microsoft', 'googl', 'alphabet',
            'tesla', 'tsla', 'amazon', 'amzn', 'meta platforms', 'nvidia', 'nvda',
            'amd', 'intel', 'netflix', 'jpmorgan', 'goldman sachs', 'berkshire',
            'fed chair', 'federal reserve', 'earnings report', 'stock rally'],
    },
];

export const getArticleCategory = (article) => {
    const text = `${article.title || ''} ${article.description || ''}`.toLowerCase();
    for (const rule of categoryRules) {
        if (rule.keywords.some(keyword => text.includes(keyword.toLowerCase()))) {
            return rule.name;
        }
    }
    return 'GENERAL';
};

export const filterNewsByCategory = (articles, category) => {
    if (category === 'all') return articles.slice(0, 25);
    return articles.filter(article => getArticleCategory(article) === category);
};
