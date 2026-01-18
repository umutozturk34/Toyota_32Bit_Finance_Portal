import { Outlet, Link } from 'react-router-dom';
import './MainLayout.css';

const MainLayout = () => {
  return (
    <div className="main-layout">
      <header className="header">
        <div className="container">
          <h1>Toyota Finance Portal</h1>
          <nav>
            <Link to="/">Home</Link>
            <Link to="/users">Users</Link>
            <Link to="/about">About</Link>
          </nav>
        </div>
      </header>
      
      <main className="main-content">
        <div className="container">
          <Outlet />
        </div>
      </main>
      
      <footer className="footer">
        <div className="container">
          <p>&copy; 2026 Toyota Finance Portal. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
};

export default MainLayout;
