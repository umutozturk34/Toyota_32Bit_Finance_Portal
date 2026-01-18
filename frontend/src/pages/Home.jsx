import './Home.css';

const Home = () => {
  return (
    <div className="home-page">
      <h1>Welcome to Toyota Finance Portal</h1>
      <p>Your comprehensive financial management solution.</p>
      
      <div className="features">
        <div className="feature-card">
          <h3>🔐 Secure Authentication</h3>
          <p>Enterprise-grade security with Keycloak integration</p>
        </div>
        <div className="feature-card">
          <h3>📊 Market Data</h3>
          <p>Real-time financial news and market information</p>
        </div>
        <div className="feature-card">
          <h3>💼 Portfolio Management</h3>
          <p>Track and manage your financial portfolio</p>
        </div>
      </div>
    </div>
  );
};

export default Home;
