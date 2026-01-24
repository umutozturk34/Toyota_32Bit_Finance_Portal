import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import axios from 'axios';
import { getToken } from '../services/keycloak';
import './TwoFactorSetup.css';

const TwoFactorSetup = () => {
  const { user } = useAuth();
  const [totpStatus, setTotpStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [setupUrl, setSetupUrl] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    fetchTotpStatus();
  }, []);

  const fetchTotpStatus = async () => {
    try {
      const token = await getToken();
      const realm = 'finance-realm';
      
      // Direkt Keycloak Account API kullan
      const response = await axios.get(
        `http://localhost:8180/realms/${realm}/account/credentials`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json'
          }
        }
      );
      
      // TOTP configured mi kontrol et
      const hasTotp = response.data.some(cred => cred.type === 'otp');
      setTotpStatus({ configured: hasTotp });
    } catch (error) {
      console.error('Failed to fetch TOTP status:', error);
      setTotpStatus({ configured: false });
    } finally {
      setLoading(false);
    }
  };

  const handleSetup2FA = () => {
    // Direkt Keycloak Account Console'a redirect
    const realm = 'finance-realm';
    const accountUrl = `http://localhost:8180/realms/${realm}/account/#/security/signingin`;
    window.location.href = accountUrl;
  };

  if (loading) {
    return (
      <div className="totp-container">
        <div className="totp-loading">Loading...</div>
      </div>
    );
  }

  return (
    <div className="totp-container">
      <div className="totp-card">
        <div className="totp-header">
          <h1>🔐 Two-Factor Authentication</h1>
          <p>Add an extra layer of security to your account</p>
        </div>

        <div className="totp-status">
          <div className="status-indicator">
            <span className={`status-badge ${totpStatus?.configured ? 'active' : 'inactive'}`}>
              {totpStatus?.configured ? '✓ Enabled' : '○ Disabled'}
            </span>
          </div>
          <p className="status-text">
            {totpStatus?.configured 
              ? 'Two-factor authentication is currently enabled on your account.'
              : 'Two-factor authentication is not enabled. Protect your account by enabling 2FA.'}
          </p>
        </div>

        <div className="totp-info">
          <h3>How it works:</h3>
          <ol>
            <li>Click "Setup 2FA" button below</li>
            <li>Scan the QR code with Google Authenticator app</li>
            <li>Enter the 6-digit code to verify</li>
            <li>From now on, you'll need the code when logging in</li>
          </ol>
        </div>

        <div className="totp-apps">
          <h3>Compatible Apps:</h3>
          <div className="app-list">
            <div className="app-item">
              <span>📱</span>
              <span>Google Authenticator</span>
            </div>
            <div className="app-item">
              <span>🔒</span>
              <span>Microsoft Authenticator</span>
            </div>
            <div className="app-item">
              <span>🛡️</span>
              <span>Authy</span>
            </div>
          </div>
        </div>

        <div className="totp-actions">
          <button onClick={handleSetup2FA} className="btn-setup">
            {totpStatus?.configured ? '⚙️ Manage 2FA' : '🚀 Setup 2FA'}
          </button>
        </div>

        <div className="totp-note">
          <p>
            <strong>Note:</strong> You'll be redirected to Keycloak Account Management 
            where you can securely set up your two-factor authentication.
          </p>
        </div>
      </div>
    </div>
  );
};

export default TwoFactorSetup;
