import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
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
      const response = await api.get('/totp/status');
      setTotpStatus(response.data.data);
    } catch (error) {
      console.error('Failed to fetch TOTP status:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSetup2FA = async () => {
    try {
      setMessage('Opening Keycloak Account Console...');
      const response = await api.get('/totp/setup-url');
      const url = response.data.data.url;
      setSetupUrl(url);
      window.location.href = url;
    } catch (error) {
      console.error('Failed to fetch setup URL:', error);
      setMessage('Failed to get setup URL. Please try again.');
    }
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
