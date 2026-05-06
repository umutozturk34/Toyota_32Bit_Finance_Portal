import { useState } from 'react';
import { motion } from 'framer-motion';
import AdminConversationList from './components/AdminConversationList';
import AdminThreadPane from './components/AdminThreadPane';

export default function AdminMessagesView() {
  const [activeUser, setActiveUser] = useState(null);

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="h-[calc(100vh-2rem)] p-3 sm:p-4"
    >
      <div className="relative rounded-3xl border border-border-default bg-bg-elevated card-hover overflow-hidden h-full grid grid-cols-1 lg:grid-cols-[340px_1fr]">
        <span aria-hidden className="absolute inset-x-0 top-0 h-[2px] bg-gradient-to-r from-transparent via-accent/60 to-transparent z-10" />
        <div className={`${activeUser ? 'hidden lg:flex' : 'flex'} flex-col min-h-0`}>
          <AdminConversationList activeUser={activeUser} onSelect={setActiveUser} />
        </div>
        <div className={`${activeUser ? 'flex' : 'hidden lg:flex'} flex-col min-h-0`}>
          <AdminThreadPane
            userSub={activeUser}
            onBack={() => setActiveUser(null)}
            onAfterDelete={() => setActiveUser(null)}
          />
        </div>
      </div>
    </motion.div>
  );
}
