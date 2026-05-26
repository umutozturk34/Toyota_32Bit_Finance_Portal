import { AnimatePresence, motion } from 'framer-motion';
import WidgetTray from './components/WidgetTray';

export default function MarketWidgetGallery({
  visible,
  sections,
  watchlists,
  onAdd,
  onDragStart,
  onDragEnd,
}) {
  return (
    <AnimatePresence initial={false}>
      {visible && (
        <motion.div
          key="gallery-sidebar"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.15, ease: 'easeOut' }}
        >
          <WidgetTray
            sections={sections}
            watchlists={watchlists}
            onAdd={onAdd}
            onDragStart={onDragStart}
            onDragEnd={onDragEnd}
          />
        </motion.div>
      )}
    </AnimatePresence>
  );
}
