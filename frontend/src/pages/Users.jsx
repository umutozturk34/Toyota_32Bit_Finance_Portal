import { useState, useEffect } from 'react';
import { userService } from '../services/userService';
import './Users.css';

const Users = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    try {
      setLoading(true);
      const response = await userService.getAllUsers();
      if (response.success) {
        setUsers(response.data);
      }
    } catch (err) {
      setError(err.message || 'Failed to fetch users');
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <div className="loading">Loading users...</div>;
  if (error) return <div className="error">Error: {error}</div>;

  return (
    <div className="users-page">
      <h1>Users Management</h1>
      
      <div className="users-grid">
        {users.length === 0 ? (
          <p>No users found.</p>
        ) : (
          users.map((user) => (
            <div key={user.id} className="user-card">
              <h3>{user.firstName} {user.lastName}</h3>
              <p><strong>Email:</strong> {user.email}</p>
              <p><strong>Role:</strong> {user.role}</p>
              <p><strong>Status:</strong> {user.active ? '✅ Active' : '❌ Inactive'}</p>
              <p className="user-date">
                <small>Joined: {new Date(user.createdAt).toLocaleDateString()}</small>
              </p>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export default Users;
