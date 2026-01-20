import { useState, useEffect } from 'react';
import { stockService } from '../services/marketService';
import './Stocks.css';

function Stocks() {
    const [stocks, setStocks] = useState([]);
    const [market, setMarket] = useState('all');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [page, setPage] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [bistIndex, setBistIndex] = useState(null);
    const [indexLoading, setIndexLoading] = useState(true);

    useEffect(() => {
        fetchBistIndex();
    }, []);

    useEffect(() => {
        fetchStocks();
    }, [market, page]);

    const fetchBistIndex = async () => {
        setIndexLoading(true);
        try {
            const response = await stockService.getBISTIndex();
            if (response.data && response.data.success) {
                setBistIndex(response.data.data);
            }
        } catch (err) {
            console.error('BIST Index yüklenemedi:', err);
        } finally {
            setIndexLoading(false);
        }
    };

    const fetchStocks = async () => {
        setLoading(true);
        setError(null);
        try {
            let response;
            if (market === 'us') {
                response = await stockService.getUSStocks(page);
            } else if (market === 'bist') {
                response = await stockService.getBISTStocks(page);
            } else if (market === 'bist-fund') {
                response = await stockService.getBISTFunds(page);
            } else {
                response = await stockService.getAllStocks(page);
            }
            
            if (response.data && response.data.success) {
                setStocks(response.data.data.content || []);
                setTotalElements(response.data.data.totalElements || 0);
                setTotalPages(response.data.data.totalPages || 0);
            }
        } catch (err) {
            setError('Hisse senedi verileri yüklenirken hata oluştu');
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

    const formatPrice = (price) => {
        return new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(price);
    };

    const formatVolume = (volume) => {
        if (volume >= 1000000) {
            return `${(volume / 1000000).toFixed(1)}M`;
        } else if (volume >= 1000) {
            return `${(volume / 1000).toFixed(1)}K`;
        }
        return volume;
    };

    if (loading && !bistIndex) {
        return <div className="loading">📊 Yükleniyor...</div>;
    }

    if (error) {
        return <div className="error">{error}</div>;
    }

    return (
        <div className="stocks-container">
            {/* BIST 100 Index Card */}
            <div className="bist-index-section">
                {indexLoading ? (
                    <div className="bist-index-card loading-card">
                        <span>BIST 100 endeksi yükleniyor...</span>
                    </div>
                ) : bistIndex ? (
                    <div className="bist-index-card">
                        <div className="index-header">
                            <div className="index-icon">📊</div>
                            <div className="index-title">
                                <h2>{bistIndex.name || 'BIST 100'}</h2>
                                <span className="index-subtitle">Borsa İstanbul Ana Endeksi</span>
                            </div>
                        </div>
                        <div className="index-main">
                            <div className="index-current">
                                <span className="index-value">{formatPrice(bistIndex.current)}</span>
                                <span className={`index-change ${getChangeClass(bistIndex.changeRate)}`}>
                                    {bistIndex.changeRate >= 0 ? '▲' : '▼'} %{Math.abs(bistIndex.changeRate).toFixed(2)}
                                </span>
                            </div>
                            <div className="index-details">
                                <div className="index-detail">
                                    <span className="label">Açılış</span>
                                    <span className="value">{formatPrice(bistIndex.opening)}</span>
                                </div>
                                <div className="index-detail">
                                    <span className="label">Kapanış</span>
                                    <span className="value">{formatPrice(bistIndex.closing)}</span>
                                </div>
                                <div className="index-detail">
                                    <span className="label">En Yüksek</span>
                                    <span className="value positive">{formatPrice(bistIndex.max)}</span>
                                </div>
                                <div className="index-detail">
                                    <span className="label">En Düşük</span>
                                    <span className="value negative">{formatPrice(bistIndex.min)}</span>
                                </div>
                            </div>
                        </div>
                        <div className="index-footer">
                            <span className="index-time">
                                📅 {bistIndex.date} • ⏰ {bistIndex.time}
                            </span>
                        </div>
                    </div>
                ) : (
                    <div className="bist-index-card error-card">
                        <span>BIST 100 endeksi yüklenemedi</span>
                    </div>
                )}
            </div>

            <div className="stocks-header">
                <h1>📈 Hisse Senetleri <span className="stock-count">({totalElements} {market === 'bist-fund' ? 'fon' : 'hisse'})</span></h1>
                <div className="market-tabs">
                    <button 
                        className={market === 'all' ? 'active' : ''}
                        onClick={() => { setMarket('all'); setPage(0); }}
                    >
                        Tümü
                    </button>
                    <button 
                        className={market === 'us' ? 'active' : ''}
                        onClick={() => { setMarket('us'); setPage(0); }}
                    >
                        🇺🇸 ABD
                    </button>
                    <button 
                        className={market === 'bist' ? 'active' : ''}
                        onClick={() => { setMarket('bist'); setPage(0); }}
                    >
                        🇹🇷 BIST
                    </button>
                    <button 
                        className={market === 'bist-fund' ? 'active' : ''}
                        onClick={() => { setMarket('bist-fund'); setPage(0); }}
                    >
                        🏢 BIST Fon
                    </button>
                </div>
                <button className="refresh-btn" onClick={fetchStocks}>
                    🔄 Yenile
                </button>
            </div>

            {loading ? (
                <div className="loading">📊 Hisseler yükleniyor...</div>
            ) : (
            <div className="stocks-grid">
                {stocks.map((stock) => (
                    <div key={stock.id} className="stock-card">
                        <div className="stock-header">
                            <div className="stock-info">
                                <h3>{stock.symbol}</h3>
                                <span className="stock-name">{stock.name}</span>
                            </div>
                            <span className={`market-badge ${stock.market.toLowerCase()}`}>
                                {stock.market}
                            </span>
                        </div>
                        
                        <div className="stock-price">
                            <div className="price-main">
                                {(stock.market === 'BIST' || stock.market === 'BIST-FUND') ? '₺' : '$'}{formatPrice(stock.price)}
                            </div>
                            <div className={`price-change ${getChangeClass(stock.changePercent)}`}>
                                {stock.changePercent > 0 ? '▲' : '▼'} 
                                {Math.abs(stock.changePercent).toFixed(2)}%
                                <span className="change-amount">
                                    ({stock.changeAmount > 0 ? '+' : ''}{(stock.market === 'BIST' || stock.market === 'BIST-FUND') ? '₺' : '$'}{formatPrice(stock.changeAmount)})
                                </span>
                            </div>
                        </div>

                        <div className="stock-details">
                            {stock.open != null && (
                            <div className="detail-row">
                                <span>Açılış:</span>
                                <span>{(stock.market === 'BIST' || stock.market === 'BIST-FUND') ? '₺' : '$'}{formatPrice(stock.open)}</span>
                            </div>
                            )}
                            {stock.high != null && (
                            <div className="detail-row">
                                <span>En Yüksek:</span>
                                <span>{(stock.market === 'BIST' || stock.market === 'BIST-FUND') ? '₺' : '$'}{formatPrice(stock.high)}</span>
                            </div>
                            )}
                            {stock.low != null && (
                            <div className="detail-row">
                                <span>En Düşük:</span>
                                <span>{(stock.market === 'BIST' || stock.market === 'BIST-FUND') ? '₺' : '$'}{formatPrice(stock.low)}</span>
                            </div>
                            )}
                            <div className="detail-row">
                                <span>Hacim:</span>
                                <span>{formatVolume(stock.volume)}</span>
                            </div>
                        </div>

                        <div className="stock-timestamp">
                            {new Date(stock.timestamp).toLocaleString('tr-TR')}
                        </div>
                    </div>
                ))}
            </div>
            )}
        </div>
    );
}

export default Stocks;
