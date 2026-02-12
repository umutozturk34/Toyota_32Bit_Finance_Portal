import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getMultipleCryptos, adminService } from '../services/marketService';
import { getCoinIds, getCoinIcon, getCoinIdBySymbol } from '../constants/coins';
import { useAuth } from '../context/AuthContext';
import './Crypto.css';

function Crypto() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [cryptos, setCryptos] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [updating, setUpdating] = useState({
        snapshot: false,
        candles: false,
        full: false,
    });

    const isAdmin = hasRole('ADMIN');
    console.log('[Crypto] isAdmin:', isAdmin);

    useEffect(() => {
        fetchCryptos();
    }, []);

    const fetchCryptos = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await getMultipleCryptos(getCoinIds());
            setCryptos(data);
        } catch (err) {
            setError('Kripto para verileri yüklenirken hata oluştu');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleCryptoSnapshotUpdate = async () => {
        setUpdating(prev => ({ ...prev, snapshot: true }));
        try {
            const response = await adminService.triggerCryptoSnapshot();
            alert(response.message || 'Kripto snapshot güncelleme başlatıldı');
            setTimeout(fetchCryptos, 5000); // 5 saniye sonra yenile
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, snapshot: false }));
        }
    };

    const handleCryptoCandlesUpdate = async () => {
        setUpdating(prev => ({ ...prev, candles: true }));
        try {
            const response = await adminService.triggerCryptoCandles();
            alert(response.message || 'Kripto candle güncelleme başlatıldı');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, candles: false }));
        }
    };

    const handleCryptoFullUpdate = async () => {
        setUpdating(prev => ({ ...prev, full: true }));
        try {
            const response = await adminService.triggerCryptoFull();
            alert(response.message || 'Kripto tam güncelleme başlatıldı');
            setTimeout(fetchCryptos, 10000); // 10 saniye sonra yenile
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

    const formatPrice = (price, currency = 'USD') => {
        if (price === null || price === undefined) return 'N/A';
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: currency,
            minimumFractionDigits: 2,
            maximumFractionDigits: currency === 'USD' ? 2 : 8
        }).format(price);
    };

    const formatPriceTRY = (price) => {
        if (price === null || price === undefined) return 'N/A';
        return new Intl.NumberFormat('tr-TR', {
            style: 'currency',
            currency: 'TRY',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(price);
    };

    const formatCompactNumber = (number) => {
        if (number === null || number === undefined) return 'N/A';
        return new Intl.NumberFormat('en-US', {
            notation: 'compact',
            compactDisplay: 'short',
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 1,
            maximumFractionDigits: 2
        }).format(number);
    };

    return (
        <div className="crypto-container">
            <div className="crypto-header">
                <h1>₿ Kripto Paralar</h1>
                <div style={{ display: 'flex', gap: '10px' }}>
                    <button className="refresh-btn" onClick={fetchCryptos} disabled={loading}>
                        🔄 Yenile
                    </button>
                    {isAdmin && (
                        <>
                            <button 
                                className="admin-btn" 
                                onClick={handleCryptoSnapshotUpdate}
                                disabled={updating.snapshot || loading}
                                title="Kripto snapshot verilerini güncelle (fiyat, hacim vb.)"
                            >
                                {updating.snapshot ? '⏳ ' : '📊 '} Snapshot
                            </button>
                            <button 
                                className="admin-btn" 
                                onClick={handleCryptoCandlesUpdate}
                                disabled={updating.candles || loading}
                                title="Kripto mum verilerini güncelle (OHLC)"
                            >
                                {updating.candles ? '⏳ ' : '📈 '} Candles
                            </button>
                            <button 
                                className="admin-btn" 
                                onClick={handleCryptoFullUpdate}
                                disabled={updating.full || loading}
                                title="Tam güncelleme (snapshot + candles)"
                            >
                                {updating.full ? '⏳ ' : '🔧 '} Full Update
                            </button>
                        </>
                    )}
                </div>
            </div>

            {loading && (
                <div className="loading" style={{ margin: '20px', padding: '15px', textAlign: 'center' }}>
                    ₿ Yükleniyor...
                </div>
            )}

            {error && (
                <div className="error" style={{ margin: '20px', padding: '15px', backgroundColor: '#fee', border: '1px solid #fcc', borderRadius: '5px' }}>
                    ⚠️ {error}
                </div>
            )}

            <div className="crypto-grid">
                {!loading && cryptos.length > 0 ? (
                    cryptos.map((crypto) => (
                        <div key={crypto.id} className="crypto-card">
                            <div className="crypto-header-row">
                                <div className="crypto-rank">#{crypto.symbol}</div>
                                <button 
                                    className="chart-btn"
                                    onClick={(e) => {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        const coinId = getCoinIdBySymbol(crypto.symbol);
                                        const chartUrl = `/chart/${coinId}?type=CRYPTO&symbol=${crypto.symbol}&range=1M`;
                                        navigate(chartUrl);
                                    }}
                                    title="Grafiği Görüntüle"
                                    style={{
                                        cursor: 'pointer',
                                        pointerEvents: 'auto'
                                    }}
                                >
                                    📊
                                </button>
                            </div>
                            
                            <div className="crypto-header-info">
                                <div className="crypto-icon">{getCoinIcon(crypto.symbol)}</div>
                                <div className="crypto-name-info">
                                    <h3>{crypto.symbol}</h3>
                                    <span className="crypto-full-name">{crypto.name}</span>
                                </div>
                            </div>

                            <div className="crypto-prices">
                                <div className="price-usd">
                                    <span className="price-label">USD:</span>
                                    <span className="price-value">{formatPrice(crypto.currentPrice, 'USD')}</span>
                                </div>
                                <div className="price-try">
                                    <span className="price-label">TRY:</span>
                                    <span className="price-value">{formatPriceTRY(crypto.currentPriceTry)}</span>
                                </div>
                            </div>

                            {crypto.changePercent !== null && crypto.changePercent !== undefined && (
                                <div className={`crypto-change ${getChangeClass(crypto.changePercent)}`}>
                                    {crypto.changePercent > 0 ? '▲' : '▼'} 
                                    {Math.abs(crypto.changePercent).toFixed(2)}%
                                    <span className="change-period">24h</span>
                                </div>
                            )}

                            <div className="crypto-details">
                                <div className="detail-item">
                                    <span className="detail-label">Change</span>
                                    <span className="detail-value">{formatPrice(crypto.changeAmount, crypto.currency)}</span>
                                </div>
                                <div className="detail-item">
                                    <span className="detail-label">Volume</span>
                                    <span className="detail-value">{formatCompactNumber(crypto.totalVolume)}</span>
                                </div>
                                <div className="detail-item">
                                    <span className="detail-label">Market Cap</span>
                                    <span className="detail-value">{formatCompactNumber(crypto.marketCap)}</span>
                                </div>
                            </div>

                            <div className="crypto-timestamp">
                                {new Date(crypto.lastUpdated).toLocaleString('tr-TR')}
                            </div>
                        </div>
                    ))
                ) : !loading ? (
                    <div style={{ 
                        padding: '40px', 
                        textAlign: 'center', 
                        color: '#666',
                        fontSize: '16px',
                        backgroundColor: '#f9f9f9',
                        borderRadius: '8px',
                        margin: '20px 0'
                    }}>
                        ₿ Henüz kripto para verisi yok. 
                        {isAdmin ? ' Admin butonlarını kullanarak veri çekebilirsiniz.' : ' Admin veri güncellemesini bekleyin.'}
                    </div>
                ) : null}
            </div>
        </div>
    );
}

export default Crypto;
