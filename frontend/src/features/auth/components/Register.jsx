import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../useAuth';
import LoadingState from '../../../shared/components/feedback/LoadingState';

const Register = () => {
  const { isAuthenticated, loading, login } = useAuth();
  const navigate = useNavigate();
  const triggered = React.useRef(false);

  React.useEffect(() => {
    if (loading) return;
    if (isAuthenticated) {
      navigate('/market', { replace: true });
      return;
    }
    if (!triggered.current) {
      triggered.current = true;
      login({ redirectUri: window.location.origin, action: 'register' });
    }
  }, [isAuthenticated, loading, login, navigate]);

  return (
    <div className="flex justify-center items-center min-h-screen min-h-[100dvh]">
      <LoadingState />
    </div>
  );
};

export default Register;
