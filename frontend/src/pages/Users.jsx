import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Users as UsersIcon, Mail, ShieldCheck, CheckCircle, XCircle, Calendar, Loader2 } from 'lucide-react';
import { userService } from '../services/userService';
const containerV = {
  hidden: {},
  show: { transition: { staggerChildren: 0.05, delayChildren: 0.1 } },
};
const cardV = {
  hidden: { opacity: 0, y: 16 },
  show:   { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] } },
};
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
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="h-8 w-8 animate-spin text-accent" />
          <span className="text-fg-muted text-sm">Loading users...</span>
        </div>
      </div>
    );
  }
  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="flex items-center gap-3 p-5 bg-danger/10 border border-danger/30 rounded-xl text-danger text-sm">
          <XCircle size={20} strokeWidth={1.8} />
          Error: {error}
        </div>
      </div>
    );
  }
  return (
    <div className="max-w-5xl mx-auto py-12">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
      >
        <div className="flex items-center gap-3 mb-10">
          <span className="flex items-center justify-center w-10 h-10 rounded-lg bg-accent/10 text-accent">
            <UsersIcon size={22} strokeWidth={1.8} />
          </span>
          <h1 className="text-2xl md:text-3xl font-bold tracking-[-0.025em] text-fg">Users Management</h1>
        </div>
        {users.length === 0 ? (
          <div className="text-center py-16 rounded-xl border border-border-default bg-bg-elevated">
            <UsersIcon size={48} strokeWidth={1.2} className="mx-auto mb-4 text-fg-subtle opacity-40" />
            <p className="text-fg-muted text-sm">No users found.</p>
          </div>
        ) : (
          <motion.div
            variants={containerV}
            initial="hidden"
            animate="show"
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
          >
            {users.map((user) => (
              <motion.div
                key={user.id}
                variants={cardV}
                className="rounded-xl border border-border-default bg-bg-elevated p-5 group card-hover transition-all duration-200 hover:border-border-hover"
              >
                <h3 className="text-fg text-base font-semibold mb-4">
                  {user.firstName} {user.lastName}
                </h3>
                <div className="flex flex-col gap-2.5">
                  <div className="flex items-center gap-2.5 text-sm">
                    <Mail size={15} strokeWidth={1.8} className="text-fg-subtle group-hover:text-accent transition-colors duration-150 shrink-0" />
                    <span className="text-fg-muted truncate">{user.email}</span>
                  </div>
                  <div className="flex items-center gap-2.5 text-sm">
                    <ShieldCheck size={15} strokeWidth={1.8} className="text-fg-subtle group-hover:text-accent transition-colors duration-150 shrink-0" />
                    <span className="text-fg-muted">{user.role}</span>
                  </div>
                  <div className="flex items-center gap-2.5 text-sm">
                    {user.active ? (
                      <>
                        <CheckCircle size={15} strokeWidth={1.8} className="text-success shrink-0" />
                        <span className="text-success font-medium">Active</span>
                      </>
                    ) : (
                      <>
                        <XCircle size={15} strokeWidth={1.8} className="text-danger shrink-0" />
                        <span className="text-danger font-medium">Inactive</span>
                      </>
                    )}
                  </div>
                  <div className="flex items-center gap-2.5 text-xs mt-1 pt-3 border-t border-border-default">
                    <Calendar size={14} strokeWidth={1.8} className="text-fg-subtle group-hover:text-accent transition-colors duration-150 shrink-0" />
                    <span className="text-fg-subtle">
                      Joined {new Date(user.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>
              </motion.div>
            ))}
          </motion.div>
        )}
      </motion.div>
    </div>
  );
};
export default Users;
