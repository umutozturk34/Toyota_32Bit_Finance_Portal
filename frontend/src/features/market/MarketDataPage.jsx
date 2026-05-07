import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Activity, LayoutGrid, Check } from 'lucide-react';
import { RefreshCw } from '../../shared/components/AnimatedIcons';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';
import SearchSuggestions from '../../shared/components/SearchSuggestions';
import { useUserLayout, useUpdateOverviewLayout } from '../../shared/hooks/useUserLayout';
import { useMarketOverview } from '../../shared/hooks/useMarketOverview';
import { definitionFor, newSectionId } from './sections/sectionRegistry';
import OverviewLayout from './OverviewLayout';
import OverviewWidgetGrid from './OverviewWidgetGrid';
import AddWidgetMenu from './AddWidgetMenu';

function nextOrder(sections) {
  return sections.reduce((max, s) => Math.max(max, s.order), -1) + 1;
}

export default function MarketDataPage() {
  const [editMode, setEditMode] = useState(false);
  const [draft, setDraft] = useState(null);
  const { isLoading: layoutLoading, overview: layout } = useUserLayout();
  const { isLoading: dataLoading, error, refetch, isFetching, widgets } = useMarketOverview();
  const updateLayout = useUpdateOverviewLayout();

  useEffect(() => {
    if (!editMode) setDraft(null);
  }, [editMode]);

  const sections = editMode ? (draft ?? layout.sections) : layout.sections;
  const newsSection = sections.find((s) => s.kind === 'NEWS');
  const newsVisible = !!newsSection?.visible;

  if (layoutLoading || dataLoading) return <LoadingState message="Piyasa özeti yükleniyor..." />;
  if (error) return <ErrorState message="Piyasa verileri yüklenemedi" onRetry={refetch} />;

  const handleChange = (next) => setDraft(next);

  const handleAdd = (option) => {
    const current = draft ?? layout.sections;
    if (option.mode === 'reveal') {
      setDraft(current.map((s) => (s.sectionId === option.sectionId ? { ...s, visible: true, order: nextOrder(current.filter((x) => x.visible)) } : s)));
      return;
    }
    const def = definitionFor(option.kind);
    const newId = newSectionId(option.kind);
    const newSection = {
      sectionId: newId,
      kind: option.kind,
      visible: true,
      order: nextOrder(current.filter((s) => s.visible)),
      config: def?.kind === 'NEWS' ? {} : option.kind === 'ASSET_CARDS' ? { assetCodes: [] } : {},
    };
    setDraft([...current, newSection]);
  };

  const handleSave = () => {
    if (draft) updateLayout.mutate({ schemaVersion: 2, sections: draft });
    setEditMode(false);
  };
  const handleCancel = () => {
    setDraft(null);
    setEditMode(false);
  };

  const header = (
    <motion.div
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
      className="flex items-center justify-between gap-3 flex-wrap"
    >
      <div className="flex items-center gap-2.5">
        <span className="flex items-center justify-center w-9 h-9 rounded-xl bg-gradient-accent text-white shadow-lg shadow-accent/25">
          <Activity className="h-4 w-4" />
        </span>
        <div>
          <h1 className="text-xl font-display font-bold tracking-tight text-fg leading-tight">Piyasa Özeti</h1>
          <p className="text-[10px] text-fg-muted">{editMode ? 'Sürükle / gizle / ayarla' : 'Canlı piyasa verileri'}</p>
        </div>
      </div>
      <div className="flex items-center gap-1.5">
        {editMode ? (
          <>
            <button onClick={handleCancel} className="flex items-center gap-1 rounded-lg border border-border-default bg-bg-elevated px-3 py-1.5 text-[11px] font-semibold text-fg-muted hover:text-fg hover:border-border-hover transition-all cursor-pointer">İptal</button>
            <button onClick={handleSave} className="flex items-center gap-1 rounded-lg border-2 border-accent bg-accent text-white px-3 py-1.5 text-[11px] font-bold tracking-wide hover:bg-accent-bright transition-all cursor-pointer shadow-lg shadow-accent/30">
              <Check className="h-3 w-3" />Bitti
            </button>
          </>
        ) : (
          <>
            <button onClick={() => setEditMode(true)} className="flex items-center gap-1 rounded-lg border border-accent/40 bg-accent/8 px-3 py-1.5 text-[11px] font-semibold text-accent hover:border-accent hover:bg-accent/15 transition-all cursor-pointer">
              <LayoutGrid className="h-3 w-3" />Düzenle
            </button>
            <button onClick={refetch} className="flex items-center gap-1 rounded-lg border border-border-default bg-bg-elevated px-3 py-1.5 text-[11px] font-semibold text-fg-muted hover:text-fg hover:border-border-hover transition-all cursor-pointer">
              <RefreshCw className={`h-3 w-3 ${isFetching ? 'animate-spin' : ''}`} />Yenile
            </button>
          </>
        )}
      </div>
      {!editMode && <div className="w-full max-w-md"><SearchSuggestions variant="hero" placeholder="Hisse, kripto, döviz, fon ara..." /></div>}
    </motion.div>
  );

  const editBar = editMode ? (
    <motion.div
      initial={{ opacity: 0, y: -4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2 }}
      className="flex items-center justify-between gap-3 rounded-lg border border-accent/30 bg-accent/5 px-3 py-2 backdrop-blur-md"
    >
      <span className="font-mono text-[10px] tracking-[0.22em] uppercase text-accent">▸ DÜZEN MODU</span>
      <AddWidgetMenu sections={sections} onPick={handleAdd} />
    </motion.div>
  ) : null;

  return (
    <OverviewLayout
      header={header}
      editBar={editBar}
      strip={<OverviewWidgetGrid sections={sections} widgets={widgets} editMode={editMode} slot="strip" onChange={handleChange} />}
      main={<OverviewWidgetGrid sections={sections} widgets={widgets} editMode={editMode} slot="main" onChange={handleChange} />}
      news={<OverviewWidgetGrid sections={sections} widgets={widgets} editMode={editMode} slot="news" onChange={handleChange} />}
      newsVisible={newsVisible || editMode}
    />
  );
}
