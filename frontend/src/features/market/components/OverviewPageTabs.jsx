import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, X, Pencil, Check } from 'lucide-react';

const MAX_NAME_LENGTH = 24;

function PageTab({ page, active, editMode, onSelect, onRename, onDelete, canDelete }) {
  const { t } = useTranslation();
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState(page.name);
  const inputRef = useRef(null);

  useEffect(() => {
    if (renaming && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select();
    }
  }, [renaming]);

  const commitRename = () => {
    const trimmed = draft.trim().slice(0, MAX_NAME_LENGTH);
    if (trimmed && trimmed !== page.name) onRename(page.id, trimmed);
    setRenaming(false);
  };

  if (renaming) {
    return (
      <div className={`flex items-center gap-1 rounded-lg border px-2 py-1.5 ${active ? 'border-accent bg-accent/15' : 'border-border-default bg-bg-elevated'}`}>
        <input
          ref={inputRef}
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value.slice(0, MAX_NAME_LENGTH))}
          onBlur={commitRename}
          onKeyDown={(e) => {
            if (e.key === 'Enter') commitRename();
            else if (e.key === 'Escape') { setDraft(page.name); setRenaming(false); }
          }}
          maxLength={MAX_NAME_LENGTH}
          className="font-display text-[12px] font-semibold bg-transparent border-none focus:outline-none text-fg w-[120px]"
        />
        <button
          type="button"
          onMouseDown={(e) => { e.preventDefault(); commitRename(); }}
          className="flex items-center justify-center w-4 h-4 rounded text-accent hover:bg-accent/20 cursor-pointer bg-transparent border-none p-0"
          aria-label={t('common.confirm', { defaultValue: 'Tamam' })}
        >
          <Check className="h-3 w-3" />
        </button>
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={() => (active && editMode ? setRenaming(true) : onSelect(page.id))}
      onDoubleClick={() => editMode && setRenaming(true)}
      className={`group/tab relative flex items-center gap-1.5 rounded-lg border px-3 py-1.5 transition-all cursor-pointer ${
        active
          ? 'border-accent bg-accent/15 text-accent shadow-sm shadow-accent/15'
          : 'border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:border-accent/40 hover:bg-accent/5'
      }`}
      title={editMode && active ? t('overviewPages.tabRenameHint', { defaultValue: 'Tıkla → Yeniden adlandır' }) : page.name}
    >
      <span className="font-display text-[12px] font-semibold tracking-tight truncate max-w-[140px]">
        {page.name}
      </span>
      {editMode && active && (
        <Pencil className="h-3 w-3 opacity-60 group-hover/tab:opacity-100 transition-opacity" />
      )}
      {editMode && canDelete && active && (
        <span
          role="button"
          tabIndex={0}
          onClick={(e) => { e.stopPropagation(); onDelete(page.id); }}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); onDelete(page.id); } }}
          className="flex items-center justify-center w-4 h-4 rounded hover:bg-danger/20 hover:text-danger transition-colors cursor-pointer p-0 ml-0.5"
          aria-label={t('overviewPages.deletePage', { defaultValue: 'Sayfayı sil' })}
        >
          <X className="h-3 w-3" />
        </span>
      )}
    </button>
  );
}

export default function OverviewPageTabs({
  pages, activePageId, editMode, canAdd, onSelect, onRename, onDelete, onAdd,
}) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-2 flex-wrap py-2">
      <AnimatePresence initial={false}>
        {pages.map((page) => (
          <motion.div
            key={page.id}
            layout
            initial={{ opacity: 0, scale: 0.92 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.92 }}
            transition={{ duration: 0.16, ease: [0.16, 1, 0.3, 1] }}
          >
            <PageTab
              page={page}
              active={page.id === activePageId}
              editMode={editMode}
              onSelect={onSelect}
              onRename={onRename}
              onDelete={onDelete}
              canDelete={pages.length > 1}
            />
          </motion.div>
        ))}
      </AnimatePresence>
      {editMode && canAdd && (
        <button
          type="button"
          onClick={onAdd}
          className="flex items-center gap-1 rounded-lg border border-dashed border-accent/40 bg-transparent px-2.5 py-1.5 text-accent hover:border-accent hover:bg-accent/10 transition-all cursor-pointer"
          title={t('overviewPages.addPage', { defaultValue: 'Yeni sayfa ekle' })}
        >
          <Plus className="h-3.5 w-3.5" />
          <span className="font-display text-[11px] font-semibold tracking-tight">
            {t('overviewPages.addPage', { defaultValue: 'Yeni sayfa' })}
          </span>
        </button>
      )}
    </div>
  );
}
