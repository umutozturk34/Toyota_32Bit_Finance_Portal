import React from 'react';
import { Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../../features/auth/useAuth';
import { ShieldOff } from 'lucide-react';
import Spinner from '../feedback/Spinner';
const ProtectedRoute = ({ children, requiredRole }) => {
  const { isAuthenticated, hasRole, loading } = useAuth();
  const { t } = useTranslation();
  // Full-screen loader ONLY until auth first resolves. Afterwards a transient `loading` flip — a background
  // Keycloak token refresh fires every few minutes — must NOT swap the whole app for a spinner: that unmounts
  // the entire layout and remounts it fresh, which (among other things) restarts the onboarding tour mid-flight.
  // Likewise, don't bounce to login while a refresh is still in flight, where isAuthenticated can dip for a frame.
  const [authSettled, setAuthSettled] = React.useState(false);
  React.useEffect(() => {
    if (isAuthenticated) setAuthSettled(true);
  }, [isAuthenticated]);
  if (loading && !isAuthenticated && !authSettled) {
    return (
      <div className="flex justify-center items-center h-screen h-[100dvh]">
        <div className="flex items-center gap-3 text-fg-muted">
          <Spinner size="md" tone="accent" />
          {t('common.loading')}
        </div>
      </div>
    );
  }
  if (!isAuthenticated && !loading) {
    return <Navigate to="/" replace />;
  }
  if (requiredRole && !hasRole(requiredRole)) {
    return (
      <div className="flex flex-col justify-center items-center h-screen h-[100dvh] gap-5">
        <ShieldOff size={48} strokeWidth={1.5} className="text-danger" />
        <h1 className="text-2xl font-semibold text-fg">{t('protectedRoute.unauthorized')}</h1>
        <p className="text-fg-muted">{t('protectedRoute.unauthorizedBody')}</p>
        <p className="text-fg-subtle text-sm">
          {t('protectedRoute.requiredRole')} <strong className="text-fg">{requiredRole}</strong>
        </p>
      </div>
    );
  }
  return children;
};
export default ProtectedRoute;
