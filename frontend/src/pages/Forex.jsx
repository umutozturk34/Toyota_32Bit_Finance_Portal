import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { forexService, adminService } from '../services/marketService';
import { getForexPairs, getForexDisplayName, getForexFlag, getBaseCurrency } from '../constants/forex';
import { useAuth } from '../context/AuthContext';
import './Stocks.css';

function Forex() {
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const [forexData, setForexData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [updating, setUpdating] = useState({
        snapshot: false,
        candles: false,
        full: false,
        tcmb: false,
    });

    const isAdmin = hasRole('ADMIN');
    console.log('[Forex] isAdmin:', isAdmin);

    useEffect(() => {
        console.log('[Forex] useEffect - fetching forex data');
        fetchForexData();
    }, []);

    const fetchForexData = async () => {
        console.log('[Forex] fetchForexData() called');
        setLoading(true);
        setError(null);
        try {
            const pairs = getForexPairs();
            console.log('[Forex] Fetching forex for pairs:', pairs);
            
            const data = await forexService.getMultipleForex(pairs);
            console.log('[Forex] fetchForexData() success, data:', data);
            setForexData(data || []);
        } catch (err) {
            console.error('[Forex] fetchForexData() error:', err);
            setError('Döviz kuru verileri yüklenirken hata oluştu');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleForexSnapshotUpdate = async () => {
        setUpdating(prev => ({ ...prev, snapshot: true }));
        try {
            const response = await adminService.triggerForexSnapshot();
            alert(response.message || 'TCMB + Yahoo snapshot güncelleme başlatıldı (~1 dakika, 21 forex × 2sn)');
            setTimeout(fetchForexData, 5000);
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, snapshot: false }));
        }
    };

    const handleForexCandlesUpdate = async () => {
        setUpdating(prev => ({ ...prev, candles: true }));
        try {
            const response = await adminService.triggerForexCandles();
            alert(response.message || 'Yahoo Finance candles güncelleme başlatıldı (~10 dakika, 20 forex × 5y OHLC)');
        } catch (err) {
            alert('Güncelleme başlatılamadı: ' + (err.response?.data?.message || err.message));
        } finally {
            setUpdating(prev => ({ ...prev, candles: false }));
        }
    };

    const handleForexFullUpdate = async () => {
        setUpdating(prev => ({ ...prev, full: true }));
        try {
            const response = await adminService.triggerForexFull();
            alert(response.message || 'Yahoo Finance FULL güncelleme başlatıldı (~12 dakika, snapshot + 5y candles)');
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
            minimumFractionDigits: 4,
            maximumFractionDigits: 4
        }).format(price);
    };

    const formatChange = (change) => {
        if (change === null || change === undefined) return 'N/A';
        const prefix = change > 0 ? '+' : '';
        return prefix + new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 4,
            maximumFractionDigits: 4
        }).format(change);
    };

    const formatPercent = (percent) => {
        if (percent === null || percent === undefined) return 'N/A';
        const prefix = percent > 0 ? '+' : '';
        return prefix + percent.toFixed(2) + '%';
    };

    const handleCardClick = (currencyCode) => {
        navigate(`/chart/${currencyCode}?type=forex`);
    };

    return (
        <div className="stocks-container">
            <div className="stocks-header">
                <h1>💱 Döviz Kurları</h1>
                <div style={{ display: 'flex', gap: '10px' }}>
                    <button className="refresh-btn" onClick={fetchForexData} disabled={loading}>
                        🔄 Yenile
                    </button>
                    {isAdmin && (
                        <>
                            <button 
                                className="admin-btn" 
                                onClick={handleForexSnapshotUpdate}
                                disabled={updating.snapshot || loading}
                                title="TCMB + Yahoo snapshot güncelle (~1 dakika, 21 forex × 2sn)"
                                style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}
                            >
                                {updating.snapshot ? '⏳' : '📊'} Snapshot
                            </button>
                            <button 
                                className="admin-btn" 
                                onClick={handleForexCandlesUpdate}
                                disabled={updating.candles || loading}
                                title="Yahoo Finance candles güncelle (~10 dakika, 20 forex × 5y OHLC)"
                                style={{ background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)' }}
                            >
                                {updating.candles ? '⏳' : '📉'} Candles
                            </button>
                            <button 
                                className="admin-btn" 
                                onClick={handleForexFullUpdate}
                                disabled={updating.full || loading}
                                title="Yahoo Finance FULL update (~12 dakika, snapshot + 5y candles)"
                                style={{ background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)' }}
                            >
                                {updating.full ? '⏳' : '📈'} Full
                            </button>
                        </>
                    )}
                </div>
            </div>

            {loading && (
                <div className="stocks-loading">
                    <div className="loading-spinner"></div>
                    <p>Döviz kurları yükleniyor...</p>
                </div>
            )}

            {error && (
                <div className="stocks-error">
                    <p>{error}</p>
                    <button onClick={fetchForexData}>Tekrar Dene</button>
                </div>
            )}

            {!loading && !error && forexData.length === 0 && (
                <div className="stocks-empty">
                    <p>Henüz döviz kuru verisi bulunmuyor.</p>
                    {isAdmin && <p>Admin panelinden güncelleme başlatabilirsiniz.</p>}
                </div>
            )}

            {!loading && !error && forexData.length > 0 && (
                <div className="stocks-grid">
                    {[...forexData].sort((a, b) => {
                        const timeA = a.yahooUpdatedAt ? new Date(a.yahooUpdatedAt).getTime() : 0;
                        const timeB = b.yahooUpdatedAt ? new Date(b.yahooUpdatedAt).getTime() : 0;
                        return timeA - timeB;
                    }).map((forex) => (
                        <div 
                            key={forex.currencyCode} 
                            className="stock-card"
                            onClick={() => handleCardClick(forex.currencyCode)}
                            style={{ cursor: 'pointer' }}
                        >
                            <div className="stock-card-header">
                                <div className="stock-info">
                                    <span className="stock-flag">{getForexFlag(forex.currencyCode)}</span>
                                    <div>
                                        <h3 className="stock-symbol">{getBaseCurrency(forex.currencyCode)} / TRY</h3>
                                        <p className="stock-name">{getForexDisplayName(forex.currencyCode)}</p>
                                    </div>
                                </div>
                            </div>

                            <div className="stock-card-body">
                                {isAdmin && forex.updatedAt && (
                                    <div style={{ 
                                        display: 'flex', 
                                        justifyContent: 'space-between', 
                                        marginBottom: '0.5rem', 
                                        padding: '0.4rem', 
                                        background: 'rgba(0,0,0,0.03)', 
                                        borderRadius: '4px',
                                        fontSize: '0.65rem',
                                        color: '#666'
                                    }}>
                                        <span>📊 Yahoo: {forex.yahooUpdatedAt ? 
                                            new Date(forex.yahooUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }) :
                                            'N/A'
                                        }</span>
                                        <span>🏦 TCMB: {forex.tcmbUpdatedAt ? 
                                            new Date(forex.tcmbUpdatedAt).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }) :
                                            'N/A'
                                        }</span>
                                    </div>
                                )}
                                
                                <div className="stock-price-section">
                                    <div className="price-row">
                                        <span className="price-label">Alış:</span>
                                        <span className="stock-price">₺ {formatPrice(forex.currentPrice)}</span>
                                    </div>
                                    {forex.sellingPrice && (
                                        <div className="price-row">
                                            <span className="price-label">Satış:</span>
                                            <span className="stock-price selling">₺ {formatPrice(forex.sellingPrice)}</span>
                                        </div>
                                    )}
                                </div>

                                {(forex.change24h !== null && forex.change24h !== undefined && 
                                  forex.changePercent24h !== null && forex.changePercent24h !== undefined) && (
                                    <div className={`stock-change ${getChangeClass(forex.change24h)}`}>
                                        <span>{formatChange(forex.change24h)} TRY</span>
                                        <span>({formatPercent(forex.changePercent24h)})</span>
                                    </div>
                                )}

                                {(forex.forexBuying || forex.forexSelling) && (
                                    <div className="stock-details">
                                        <h4 style={{ fontSize: '0.8rem', marginBottom: '0.3rem', color: '#666' }}>TCMB Kurları</h4>
                                        {forex.forexBuying && (
                                            <div className="detail-row">
                                                <span>Döviz Alış:</span>
                                                <span>₺ {formatPrice(forex.forexBuying)}</span>
                                            </div>
                                        )}
                                        {forex.forexSelling && (
                                            <div className="detail-row">
                                                <span>Döviz Satış:</span>
                                                <span>₺ {formatPrice(forex.forexSelling)}</span>
                                            </div>
                                        )}
                                        {forex.banknoteBuying && (
                                            <div className="detail-row">
                                                <span>Efektif Alış:</span>
                                                <span>₺ {formatPrice(forex.banknoteBuying)}</span>
                                            </div>
                                        )}
                                        {forex.banknoteSelling && (
                                            <div className="detail-row">
                                                <span>Efektif Satış:</span>
                                                <span>₺ {formatPrice(forex.banknoteSelling)}</span>
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>

                            {!isAdmin && forex.updatedAt && (
                                <div className="stock-card-footer">
                                    <span className="stock-updated" style={{ fontSize: '0.7rem', color: '#999' }}>
                                        🕐 Son Güncelleme: {(() => {
                                            const tcmbTime = forex.tcmbUpdatedAt ? new Date(forex.tcmbUpdatedAt) : null;
                                            const yahooTime = forex.yahooUpdatedAt ? new Date(forex.yahooUpdatedAt) : null;
                                            const lastUpdate = tcmbTime && yahooTime ? 
                                                (tcmbTime > yahooTime ? tcmbTime : yahooTime) :
                                                (tcmbTime || yahooTime || new Date(forex.updatedAt));
                                            return lastUpdate.toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
                                        })()}
                                    </span>
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

export default Forex;
