import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../../../features/auth/AuthContext';
import { ShieldOff } from 'lucide-react';
import { Loader2 } from '../feedback/AnimatedIcons';
const ProtectedRoute = ({ children, requiredRole }) => {
  const { isAuthenticated, hasRole, loading } = useAuth();
  if (loading) {
    return (
      <div className="flex justify-center items-center h-screen">
        <div className="flex items-center gap-3 text-fg-muted">
          <Loader2 size={20} className="animate-spin text-accent" />
          Loading...
        </div>
      </div>
    );
  }
  if (!isAuthenticated) {
    return <Navigate to="/" replace />;
  }
  if (requiredRole && !hasRole(requiredRole)) {
    return (
      <div className="flex flex-col justify-center items-center h-screen gap-5">
        <ShieldOff size={48} strokeWidth={1.5} className="text-danger" />
        <h1 className="text-2xl font-semibold text-fg">Unauthorized</h1>
        <p className="text-fg-muted">You don&apos;t have permission to access this page.</p>
        <p className="text-fg-subtle text-sm">
          Required role: <strong className="text-fg">{requiredRole}</strong>
        </p>
      </div>
    );
  }
  return children;
};
export default ProtectedRoute;
