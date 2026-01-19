import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import './Register.css';

const Register = () => {
  const { isAuthenticated, login } = useAuth();
  const navigate = useNavigate();

  React.useEffect(() => {
    if (isAuthenticated) {
      navigate('/users');
    }
  }, [isAuthenticated, navigate]);

  const handleRegister = () => {
    login({ redirectUri: window.location.origin, action: 'register' });
  };

  if (isAuthenticated) {
    return null;
  }

  return (
    <div className="register-container">
      <div className="register-card">
        <div className="register-header">
          <h1>🏦 Finance Portal</h1>
          <p>Create Your Account</p>
        </div>

        <div className="register-description">
          <p>Join Finance Portal and start managing your financial data securely.</p>
        </div>

        <button onClick={handleRegister} className="register-button">
          ✍️ Register with Keycloak
        </button>

        <div className="register-features">
          <div className="feature-item">
            <span className="feature-icon">🔐</span>
            <div>
              <h4>Secure Authentication</h4>
              <p>Enterprise-grade Keycloak security</p>
            </div>
          </div>
          <div className="feature-item">
            <span className="feature-icon">🎯</span>
            <div>
              <h4>Role-Based Access</h4>
              <p>Granular permission control</p>
            </div>
          </div>
          <div className="feature-item">
            <span className="feature-icon">🔒</span>
            <div>
              <h4>2FA Support</h4>
              <p>Two-factor authentication ready</p>
            </div>
          </div>
        </div>

        <div className="register-footer">
          <p>Already have an account? <a href="/login">Login here</a></p>
        </div>
      </div>
    </div>
  );
};

export default Register;
