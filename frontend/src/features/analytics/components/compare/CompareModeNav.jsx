import { MODES } from '../../lib/compareConstants';

export default function CompareModeNav({ mode, switchMode, t }) {
  return (
    <nav className="flex items-center gap-1 flex-wrap">
      {MODES.map(({ id, labelKey, Icon }) => {
        const active = mode === id;
        return (
          <button
            key={id}
            type="button"
            onClick={() => switchMode(id)}
            className={`relative flex items-center gap-2 px-3 sm:px-4 py-2 text-sm font-semibold rounded-lg cursor-pointer border-none transition-colors whitespace-nowrap ${
              active ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]' : 'text-fg-muted hover:text-fg'
            }`}
          >
            <Icon className="h-4 w-4" />
            {t(`analytics.${labelKey}`, { defaultValue: id })}
          </button>
        );
      })}
    </nav>
  );
}
