import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import './Login.css';

const Login = () => {
  const { isAuthenticated, user, login } = useAuth();
  const navigate = useNavigate();

  React.useEffect(() => {
    if (isAuthenticated) {
      navigate('/users');
    }
  }, [isAuthenticated, navigate]);

  if (isAuthenticated) {
    return null;
  }

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h1>🏦 Finance Portal</h1>
          <p>Secure Authentication with Keycloak</p>
        </div>

        <div className="login-body">
          <div className="login-info">
            <h3>🔐 Authentication Required</h3>
            <p>Please login to access the Finance Portal.</p>
            
            <div className="feature-list">
              <div className="feature-item">
                <span className="icon">✅</span>
                <span>Secure JWT Authentication</span>
              </div>
              <div className="feature-item">
                <span className="icon">🔑</span>
                <span>Role-Based Access Control</span>
              </div>
              <div className="feature-item">
                <span className="icon">🔐</span>
                <span>Two-Factor Authentication (2FA)</span>
              </div>
              <div className="feature-item">
                <span className="icon">🌐</span>
                <span>Single Sign-On (SSO)</span>
              </div>
            </div>
          </div>

          <button className="login-button" onClick={login}>
            <span className="button-icon">🚀</span>
            Login with Keycloak
          </button>

          <div className="test-accounts">
            <h4>Test Accounts:</h4>
            <div className="account">
              <strong>Admin:</strong> admin / admin123 
              <span className="badge">ADMIN + USER</span>
            </div>
            <div className="account">
              <strong>User:</strong> user / user123
              <span className="badge">USER</span>
            </div>
          </div>
        </div>

        <div className="login-footer">
          <p>Powered by Keycloak & Spring Security</p>
        </div>
      </div>
    </div>
  );
};

export default Login;
