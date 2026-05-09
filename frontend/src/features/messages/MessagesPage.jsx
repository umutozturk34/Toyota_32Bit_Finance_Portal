import { useAuth } from '../auth/AuthContext';
import UserMessagesView from './views/UserMessagesView';
import AdminMessagesView from './views/AdminMessagesView';

export default function MessagesPage() {
  const { hasRole } = useAuth();
  return hasRole('ADMIN') ? <AdminMessagesView /> : <UserMessagesView />;
}
