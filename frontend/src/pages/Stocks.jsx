import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { stockService, adminService } from '../services/marketService';
import { getBistSymbols } from '../constants/stocks';
import { useAuth } from '../context/AuthContext';
import './Stocks.css';

function Stocks() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [stocks, setStocks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [updating, setUpdating] = useState({
        snapshot: false,
        candles: false,
        full: false,
    });

    const isAdmin = hasRole('ADMIN');
    console.log('[Stocks] isAdmin:', isAdmin);

    useEffect(() => {
        console.log('[Stocks] useEffect - fetching stocks');
        fetchStocks();
    }, []);

    const fetchStocks = async () => {
        console.log('[Stocks] fetchStocks() called');
        setLoading(true);
        setError(null);
        try {
            // Get stock symbols from constants (matches backend env)
            const symbols = getBistSymbols();
            console.log('[Stocks] Fetching stocks for symbols:', symbols);
            
            // Fetch each stock individually (uses cache)
            const data = await stockService.getMultipleStocks(symbols);
            console.log('[Stocks] fetchStocks() success, data:', data);
            setStocks(data || []);
        } catch (err) {
            console.error('[Stocks] fetchStocks() error:', err);
            setError('Hisse senedi verileri yüklenirken hata oluştu');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const indices = stocks.filter(s => ['XU030.IS', 'XU100.IS', 'XU500.IS'].includes(s.symbol));
    const regularStocks = stocks.filter(s => !['XU030.IS', 'XU100.IS', 'XU500.IS'].includes(s.symbol));

    const handleStockSnapshotUpdate = async () => {
        setUpdating(prev => ({ ...prev, snapshot: true }));
        try {
            const response = await adminService.triggerStockSnapshot();
            alert(response.message || 'Hisse snapshot güncelleme başlatıldı');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, snapshot: false }));
        }
    };

    const handleStockCandlesUpdate = async () => {
        setUpdating(prev => ({ ...prev, candles: true }));
        try {
            const response = await adminService.triggerStockCandles();
            alert(response.message || 'Hisse candle güncelleme başlatıldı (Bu işlem 10-15 dakika sürebilir)');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, candles: false }));
        }
    };

    const handleStockFullUpdate = async () => {
        setUpdating(prev => ({ ...prev, full: true }));
        try {
            const response = await adminService.triggerStockFull();
            alert(response.message || 'Hisse tam güncelleme başlatıldı (Bu işlem 15-20 dakika sürebilir)');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, full: false }));
        }
    };

    const getChangeClass = (change) => {
        if (change > 0) return 'positive';
        if (change < 0) return 'negative';
        return 'neutral';
    };

    const formatPrice = (price) => {
        if (price === null || price === undefined) return 'N/A';
        return new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(price);
    };

    const formatVolume = (volume) => {
        if (!volume) return 'N/A';
        if (volume >= 1000000) {
            return `${(volume / 1000000).toFixed(1)}M`;
        } else if (volume >= 1000) {
            return `${(volume / 1000).toFixed(1)}K`;
        }
        return volume;
    };

    return (
        <div className="stocks-container">
            <div className="stocks-header">
                <h1>� Borsa İstanbul (BIST)</h1>
                <div style={{ display: 'flex', gap: '10px' }}>
                    <button className="refresh-btn" onClick={fetchStocks} disabled={loading}>
                        🔄 Yenile
                    </button>
                    {isAdmin && (
                        <>
                            <button 
                                className="admin-btn" 
                                onClick={handleStockSnapshotUpdate}
                                disabled={updating.snapshot || loading}
                                title="Hisse snapshot verilerini güncelle (fiyat, hacim vb.)"
                            >
                                {updating.snapshot ? '⏳ ' : '📊 '} Snapshot
                            </button>
                            <button 
                                className="admin-btn" 
                                onClick={handleStockCandlesUpdate}
                                disabled={updating.candles || loading}
                                title="5 yıllık OHLC verilerini güncelle (10-15 dakika)"
                            >
                                {updating.candles ? '⏳ ' : '📈 '} Candles (5y)
                            </button>
                            <button 
                                className="admin-btn" 
                                onClick={handleStockFullUpdate}
                                disabled={updating.full || loading}
                                title="Tam güncelleme (snapshot + 5y candles, 15-20 dakika)"
                            >
                                {updating.full ? '⏳ ' : '🔧 '} Full Update
                            </button>
                        </>
                    )}
                </div>
            </div>
            
            {loading && (
                <div className="loading" style={{ margin: '20px', padding: '15px', textAlign: 'center' }}>
                    📊 Yükleniyor...
                </div>
            )}
            
            {error && (
                <div className="error" style={{ margin: '20px', padding: '15px', backgroundColor: '#fee', border: '1px solid #fcc', borderRadius: '5px' }}>
                    ⚠️ {error}
                </div>
            )}

            {/* BIST Endeksleri */}
            {!loading && stocks.length > 0 && indices.length === 0 && (
                <div className="info" style={{ margin: '20px', padding: '15px', backgroundColor: '#fff3cd', border: '1px solid #ffc107', borderRadius: '5px', color: '#856404' }}>
                    ℹ️ Endeks verileri bulunamadı. Admin snapshot güncellemesi ile endeksler (BIST 30, BIST 100, BIST 500) çekilebilir.
                </div>
            )}
            
            {!loading && indices.length > 0 && (
                <>
                    <div className="indices-header">
                        <h2>📊 BIST Endeksleri</h2>
                    </div>
                    <div className="indices-grid">
                        {indices.map((index) => (
                            <div key={index.symbol} className="index-card">
                                <div className="index-info">
                                    <h3>
                                        {index.symbol === 'XU030.IS' ? 'BIST 30' : 
                                         index.symbol === 'XU100.IS' ? 'BIST 100' : 
                                         'BIST 500'}
                                    </h3>
                                    <span className="index-code">{index.symbol}</span>
                                </div>
                                <button 
                                    className="chart-btn"
                                    onClick={() => {
                                        navigate(`/chart/${index.symbol}?type=STOCK&symbol=${index.symbol}&range=3M`);
                                    }}
                                    title="Grafiği Görüntüle"
                                    style={{ position: 'absolute', top: '15px', right: '15px' }}
                                >
                                    📊
                                </button>
                                <div className="index-price">
                                    {formatPrice(index.currentPrice)}
                                </div>
                                <div className={`index-change ${getChangeClass(index.priceChangePercent)}`}>
                                    {index.priceChangePercent > 0 ? '▲' : '▼'} 
                                    {Math.abs(index.priceChangePercent || 0).toFixed(2)}%
                                </div>
                            </div>
                        ))}
                    </div>
                </>
            )}
            
            {/* BIST Hisse Senetleri */}
            {!loading && regularStocks.length > 0 && (
                <div className="indices-header" style={{ marginTop: '2rem' }}>
                    <h2>📈 BIST Hisse Senetleri <span className="stock-count">({regularStocks.length} hisse)</span></h2>
                </div>
            )}
            
            <div className="stocks-grid">
                {!loading && regularStocks.length > 0 ? 
                    regularStocks.map((stock) => (
                        <div key={stock.symbol} className="stock-card">
                            <div className="stock-header">
                                <div className="stock-info">
                                    <h3>{stock.symbol}</h3>
                                    <span className="stock-name">{stock.name}</span>
                                </div>
                                <div className="header-actions">
                                    <span className="market-badge bist">
                                        {stock.exchange || 'BIST'}
                                    </span>
                                    <button 
                                        className="chart-btn"
                                        onClick={() => {
                                            navigate(`/chart/${stock.symbol}?type=STOCK&symbol=${stock.symbol}&range=3M`);
                                        }}
                                        title="Grafiği Görüntüle"
                                    >
                                        📊
                                    </button>
                                </div>
                            </div>
                            
                            <div className="stock-price">
                                <div className="price-main">
                                    ₺{formatPrice(stock.currentPrice)}
                                </div>
                                <div className={`price-change ${getChangeClass(stock.priceChangePercent)}`}>
                                    {stock.priceChangePercent > 0 ? '▲' : '▼'} 
                                    {Math.abs(stock.priceChangePercent || 0).toFixed(2)}%
                                    <span className="change-amount">
                                        ({stock.priceChangeAmount > 0 ? '+' : ''}₺{formatPrice(stock.priceChangeAmount)})
                                    </span>
                                </div>
                            </div>

                            <div className="stock-details">
                                {stock.openPrice != null && (
                                <div className="detail-row">
                                    <span>Açılış:</span>
                                    <span>₺{formatPrice(stock.openPrice)}</span>
                                </div>
                                )}
                                {stock.dayHigh != null && (
                                <div className="detail-row">
                                    <span>En Yüksek:</span>
                                    <span>₺{formatPrice(stock.dayHigh)}</span>
                                </div>
                                )}
                                {stock.dayLow != null && (
                                <div className="detail-row">
                                    <span>En Düşük:</span>
                                    <span>₺{formatPrice(stock.dayLow)}</span>
                                </div>
                                )}
                                <div className="detail-row">
                                    <span>Hacim:</span>
                                    <span>{formatVolume(stock.volume)}</span>
                                </div>
                            </div>

                            <div className="stock-timestamp">
                                {stock.lastUpdated ? new Date(stock.lastUpdated).toLocaleString('tr-TR') : 'N/A'}
                            </div>
                        </div>
                    ))
                : !loading ? (
                    <div style={{ 
                        padding: '40px', 
                        textAlign: 'center', 
                        color: '#666',
                        fontSize: '16px',
                        backgroundColor: '#f9f9f9',
                        borderRadius: '8px',
                        margin: '20px 0'
                    }}>
                        📊 Henüz hisse senedi verisi yok. 
                        {isAdmin ? ' Admin butonlarını kullanarak veri çekebilirsiniz.' : ' Admin veri güncellemesini bekleyin.'}
                    </div>
                ) : null}
            </div>
        </div>
    );
}

export default Stocks;
