import { useAuth } from '../auth/AuthContext';
import UserMessagesView from './UserMessagesView';
import AdminMessagesView from './AdminMessagesView';

export default function MessagesPage() {
  const { hasRole } = useAuth();
  return hasRole('ADMIN') ? <AdminMessagesView /> : <UserMessagesView />;
}
