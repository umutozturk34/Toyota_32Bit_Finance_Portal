import { useState, useEffect } from 'react';
import { cryptoService } from '../services/marketService';
import './Crypto.css';

function Crypto() {
    const [cryptos, setCryptos] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [page, setPage] = useState(0);

    useEffect(() => {
        fetchCryptos();
    }, [page]);

    const fetchCryptos = async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await cryptoService.getLatestCryptos(page);
            if (response.data && response.data.success) {
                setCryptos(response.data.data.content || []);
            }
        } catch (err) {
            setError('Kripto para verileri yüklenirken hata oluştu');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const getChangeClass = (change) => {
        if (change > 0) return 'positive';
        if (change < 0) return 'negative';
        return 'neutral';
    };

    const formatPrice = (price, currency = 'USD') => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: currency,
            minimumFractionDigits: 2,
            maximumFractionDigits: currency === 'USD' ? 2 : 8
        }).format(price);
    };

    const formatPriceTRY = (price) => {
        return new Intl.NumberFormat('tr-TR', {
            style: 'currency',
            currency: 'TRY',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(price);
    };

    const formatMarketCap = (marketCap) => {
        if (marketCap >= 1000000000000) {
            return `$${(marketCap / 1000000000000).toFixed(2)}T`;
        } else if (marketCap >= 1000000000) {
            return `$${(marketCap / 1000000000).toFixed(2)}B`;
        } else if (marketCap >= 1000000) {
            return `$${(marketCap / 1000000).toFixed(2)}M`;
        }
        return `$${marketCap.toFixed(0)}`;
    };

    const getCryptoIcon = (symbol) => {
        const icons = {
            'BTC': '₿',
            'ETH': 'Ξ',
            'BNB': '🔶',
            'XRP': '✕',
            'ADA': '₳',
            'SOL': '◎',
            'DOT': '●',
            'DOGE': 'Ð',
            'MATIC': '◆',
            'LTC': 'Ł'
        };
        return icons[symbol.toUpperCase()] || '₿';
    };

    if (loading) {
        return <div className="loading">₿ Yükleniyor...</div>;
    }

    if (error) {
        return <div className="error">{error}</div>;
    }

    return (
        <div className="crypto-container">
            <div className="crypto-header">
                <h1>₿ Kripto Paralar</h1>
                <button className="refresh-btn" onClick={fetchCryptos}>
                    🔄 Yenile
                </button>
            </div>

            <div className="crypto-grid">
                {cryptos.map((crypto) => (
                    <div key={crypto.id} className="crypto-card">
                        <div className="crypto-rank">#{crypto.marketCapRank}</div>
                        
                        <div className="crypto-header-info">
                            <div className="crypto-icon">{getCryptoIcon(crypto.symbol)}</div>
                            <div className="crypto-name-info">
                                <h3>{crypto.symbol}</h3>
                                <span className="crypto-full-name">{crypto.name}</span>
                            </div>
                        </div>

                        <div className="crypto-prices">
                            <div className="price-usd">
                                <span className="price-label">USD:</span>
                                <span className="price-value">{formatPrice(crypto.priceUsd)}</span>
                            </div>
                            <div className="price-try">
                                <span className="price-label">TRY:</span>
                                <span className="price-value">{formatPriceTRY(crypto.priceTry)}</span>
                            </div>
                        </div>

                        <div className={`crypto-change ${getChangeClass(crypto.changePercent24h)}`}>
                            {crypto.changePercent24h > 0 ? '▲' : '▼'} 
                            {Math.abs(crypto.changePercent24h).toFixed(2)}%
                            <span className="change-period">24h</span>
                        </div>

                        <div className="crypto-details">
                            <div className="detail-item">
                                <span className="detail-label">Market Cap</span>
                                <span className="detail-value">{formatMarketCap(crypto.marketCapUsd)}</span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">24h Hacim</span>
                                <span className="detail-value">{formatMarketCap(crypto.volume24hUsd)}</span>
                            </div>
                        </div>

                        <div className="crypto-timestamp">
                            {new Date(crypto.timestamp).toLocaleString('tr-TR')}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

export default Crypto;
