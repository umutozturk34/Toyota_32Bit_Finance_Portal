import React, { useState, useEffect } from 'react';
import { metalService } from '../services/dataService';
import './Metals.css';

const Metals = () => {
    const [metals, setMetals] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        fetchMetals();
    }, []);

    const fetchMetals = async () => {
        setLoading(true);
        setError(null);
        
        try {
            const response = await metalService.getLatestPrices();
            
            if (response.success && response.data) {
                setMetals(response.data || []);
            } else {
                setError('Failed to load precious metals data');
            }
        } catch (err) {
            console.error('Error fetching metals:', err);
            setError('Failed to load precious metals data. Please try again later.');
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
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(price);
    };

    const getMetalIcon = (symbol) => {
        switch (symbol) {
            case 'PAXG':
            case 'XAUT':
            case 'GOLD': return '🥇';
            case 'KAG':
            case 'SILVER': return '🥈';
            case 'PLATINUM': return '⭐';
            default: return '💎';
        }
    };

    const getMetalColor = (symbol) => {
        switch (symbol) {
            case 'PAXG':
            case 'XAUT':
            case 'GOLD': return '#FFD700';
            case 'KAG':
            case 'SILVER': return '#C0C0C0';
            case 'PLATINUM': return '#E5E4E2';
            default: return '#667eea';
        }
    };

    if (loading) {
        return (
            <div className="metals-container">
                <div className="metals-loading">💎 Loading precious metals...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="metals-container">
                <div className="metals-error">❌ {error}</div>
            </div>
        );
    }

    return (
        <div className="metals-container">
            <div className="metals-header">
                <h1>💰 Kıymetli Madenler</h1>
                <p>Altın, Gümüş ve diğer değerli metallerin canlı fiyatları</p>
                <button className="refresh-btn" onClick={fetchMetals}>
                    🔄 Yenile
                </button>
            </div>

            {metals.length === 0 ? (
                <div className="metals-empty">Şu anda kıymetli maden verisi mevcut değil.</div>
            ) : (
                <div className="metals-grid">
                    {metals.map((metal) => (
                        <div 
                            key={metal.id} 
                            className="metal-card"
                            style={{ borderTop: `4px solid ${getMetalColor(metal.symbol)}` }}
                        >
                            <div className="metal-header">
                                <div className="metal-icon">
                                    {getMetalIcon(metal.symbol)}
                                </div>
                                <div className="metal-info">
                                    <h3>{metal.symbol}</h3>
                                    <span className="metal-name">{metal.name}</span>
                                </div>
                            </div>
                            
                            <div className="metal-price">
                                <div className="price-main">
                                    {formatPrice(metal.priceUsd)}
                                </div>
                                <div className={`price-change ${getChangeClass(metal.changePercent)}`}>
                                    {metal.changePercent > 0 ? '▲' : '▼'} 
                                    {Math.abs(metal.changePercent).toFixed(2)}%
                                    <span className="change-amount">
                                        ({metal.changeAmount > 0 ? '+' : ''}{formatPrice(metal.changeAmount)})
                                    </span>
                                </div>
                            </div>

                            <div className="metal-timestamp">
                                Son güncelleme: {new Date(metal.timestamp).toLocaleString('tr-TR')}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default Metals;