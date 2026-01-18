import './About.css';

const About = () => {
  return (
    <div className="about-page">
      <h1>About Toyota Finance Portal</h1>
      
      <div className="about-content">
        <section>
          <h2>Project Overview</h2>
          <p>
            Toyota Finance Portal is a comprehensive financial management system 
            designed for Toyota dealerships and financial services. This application 
            provides secure authentication, real-time market data, and portfolio 
            management capabilities.
          </p>
        </section>

        <section>
          <h2>Technology Stack</h2>
          <ul>
            <li><strong>Frontend:</strong> React + Vite</li>
            <li><strong>Backend:</strong> Spring Boot 3.4</li>
            <li><strong>Database:</strong> PostgreSQL 15</li>
            <li><strong>Authentication:</strong> Keycloak (planned)</li>
            <li><strong>Deployment:</strong> Docker + Docker Compose</li>
          </ul>
        </section>

        <section>
          <h2>Version</h2>
          <p>Current Version: <strong>v0.1.0</strong> (In Development)</p>
        </section>
      </div>
    </div>
  );
};

export default About;
