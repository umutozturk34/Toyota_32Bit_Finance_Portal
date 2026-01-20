import React, { useState, useEffect } from 'react';
import { exchangeRateService, metalService } from '../services/dataService';
import './MarketData.css';

const MarketData = () => {
    const [rates, setRates] = useState([]);
    const [metals, setMetals] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [activeTab, setActiveTab] = useState('forex');

    useEffect(() => {
        fetchData();
    }, []);

    const fetchData = async () => {
        setLoading(true);
        setError(null);
        
        try {
            const [ratesResponse, metalsResponse] = await Promise.all([
                exchangeRateService.getLatestRates(),
                metalService.getLatestPrices().catch(() => ({ success: false, data: [] }))
            ]);
            
            if (ratesResponse.success && ratesResponse.data) {
                setRates(ratesResponse.data);
            }
            
            if (metalsResponse.success && metalsResponse.data) {
                setMetals(metalsResponse.data);
            }
        } catch (err) {
            console.error('Error fetching data:', err);
            setError('Failed to load market data. Please try again later.');
        } finally {
            setLoading(false);
        }
    };

    const getCurrencyFlag = (code) => {
        const flags = {
            'USD': '🇺🇸',
            'EUR': '🇪🇺',
            'GBP': '🇬🇧',
            'JPY': '🇯🇵',
            'CHF': '🇨🇭',
            'CAD': '🇨🇦',
            'AUD': '🇦🇺',
            'SAR': '🇸🇦',
            'KWD': '🇰🇼',
            'SEK': '🇸🇪',
            'NOK': '🇳🇴',
            'DKK': '🇩🇰'
        };
        return flags[code] || '💱';
    };

    const formatRate = (rate) => {
        return parseFloat(rate).toFixed(4);
    };

    const formatDate = (dateString) => {
        const date = new Date(dateString);
        return date.toLocaleDateString('tr-TR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    };

    const getMetalIcon = (symbol) => {
        const icons = {
            'PAXG': '🥇',
            'XAUT': '🥇',
            'KAG': '🥈',
            'GOLD': '🥇',
            'SILVER': '🥈',
            'PLATINUM': '⭐'
        };
        return icons[symbol] || '💎';
    };

    const getMetalName = (symbol) => {
        const names = {
            'PAXG': 'PAX Gold (Altın)',
            'XAUT': 'Tether Gold (Altın)',
            'KAG': 'Kinesis Silver (Gümüş)',
            'GOLD': 'Altın',
            'SILVER': 'Gümüş',
            'PLATINUM': 'Platin'
        };
        return names[symbol] || symbol;
    };

    if (loading) {
        return (
            <div className="market-container">
                <div className="market-loading">💱 Loading market data...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="market-container">
                <div className="market-error">❌ {error}</div>
                <button className="refresh-button" onClick={fetchData}>
                    🔄 Retry
                </button>
            </div>
        );
    }

    return (
        <div className="market-container">
            <div className="market-header">
                <h1>💱 Market Data</h1>
                <p>Exchange rates & precious metals prices</p>
            </div>

            <div className="market-tabs">
                <button 
                    className={activeTab === 'forex' ? 'active' : ''}
                    onClick={() => setActiveTab('forex')}
                >
                    💱 Döviz Kurları
                </button>
                <button 
                    className={activeTab === 'metals' ? 'active' : ''}
                    onClick={() => setActiveTab('metals')}
                >
                    🥇 Kıymetli Madenler
                </button>
            </div>

            <button className="refresh-button" onClick={fetchData}>
                🔄 Refresh Data
            </button>

            {activeTab === 'forex' && (
                <>
                    {rates.length === 0 ? (
                        <div className="market-empty">No exchange rates available at the moment.</div>
                    ) : (
                        <div className="market-grid">
                            {rates.map((rate) => (
                                <div key={rate.id} className="currency-card">
                                    <div className="currency-header">
                                        <span className="currency-flag">
                                            {getCurrencyFlag(rate.currencyCode)}
                                        </span>
                                        <div className="currency-info">
                                            <h3>{rate.currencyName}</h3>
                                            <div className="currency-code">{rate.currencyCode}/TRY</div>
                                        </div>
                                    </div>
                                    
                                    <div className="currency-rates">
                                        <div className="rate-row">
                                            <span className="rate-label">Buying</span>
                                            <span className="rate-value rate-buy">
                                                ₺{formatRate(rate.buyingRate)}
                                            </span>
                                        </div>
                                        <div className="rate-row">
                                            <span className="rate-label">Selling</span>
                                            <span className="rate-value rate-sell">
                                                ₺{formatRate(rate.sellingRate)}
                                            </span>
                                        </div>
                                    </div>
                                    
                                    <div className="currency-date">
                                        {formatDate(rate.rateDate)}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </>
            )}

            {activeTab === 'metals' && (
                <>
                    {metals.length === 0 ? (
                        <div className="market-empty">
                            <p>🥇 Kıymetli maden verileri yükleniyor...</p>
                            <p className="market-hint">Veriler her 15 dakikada bir güncellenir.</p>
                        </div>
                    ) : (
                        <div className="market-grid metals-grid">
                            {metals.map((metal) => (
                                <div key={metal.id} className={`metal-card ${metal.symbol?.toLowerCase()}`}>
                                    <div className="metal-header">
                                        <span className="metal-icon">
                                            {getMetalIcon(metal.symbol)}
                                        </span>
                                        <div className="metal-info">
                                            <h3>{getMetalName(metal.symbol)}</h3>
                                            <div className="metal-symbol">{metal.symbol}</div>
                                        </div>
                                    </div>
                                    
                                    <div className="metal-price">
                                        <span className="price-value">
                                            ${parseFloat(metal.priceUsd).toLocaleString('en-US', { 
                                                minimumFractionDigits: 2, 
                                                maximumFractionDigits: 2 
                                            })}
                                        </span>
                                    </div>
                                    
                                    <div className={`metal-change ${metal.changePercent >= 0 ? 'positive' : 'negative'}`}>
                                        {metal.changePercent >= 0 ? '📈' : '📉'} 
                                        {metal.changePercent >= 0 ? '+' : ''}{parseFloat(metal.changePercent).toFixed(2)}%
                                    </div>
                                    
                                    <div className="metal-date">
                                        {formatDate(metal.timestamp)}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

export default MarketData;
