import { useRef, useCallback, useEffect } from 'react';

const PRESETS = [10, 25, 50, 75, 90, 100];

const COLOR_VARS = {
  success: { track: 'bg-success', thumb: 'border-success bg-success', preset: 'bg-success text-white', inactive: 'bg-bg-base text-fg-muted hover:text-fg border border-border-default' },
  danger: { track: 'bg-danger', thumb: 'border-danger bg-danger', preset: 'bg-danger text-white', inactive: 'bg-bg-base text-fg-muted hover:text-fg border border-border-default' },
  accent: { track: 'bg-accent', thumb: 'border-accent bg-accent', preset: 'bg-accent text-white', inactive: 'bg-bg-base text-fg-muted hover:text-fg border border-border-default' },
};

export default function PercentageSlider({ value = 0, onChange, color = 'accent' }) {
  const trackRef = useRef(null);
  const dragging = useRef(false);
  const colors = COLOR_VARS[color] || COLOR_VARS.accent;

  const clamp = (v) => Math.max(0, Math.min(100, v));

  const getPercentFromEvent = useCallback((e) => {
    const rect = trackRef.current.getBoundingClientRect();
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const pct = ((clientX - rect.left) / rect.width) * 100;
    return Math.round(clamp(pct));
  }, []);

  const handlePointerDown = useCallback((e) => {
    e.preventDefault();
    dragging.current = true;
    onChange(getPercentFromEvent(e));
  }, [onChange, getPercentFromEvent]);

  useEffect(() => {
    const handleMove = (e) => {
      if (!dragging.current) return;
      e.preventDefault();
      onChange(getPercentFromEvent(e));
    };
    const handleUp = () => { dragging.current = false; };

    window.addEventListener('mousemove', handleMove);
    window.addEventListener('mouseup', handleUp);
    window.addEventListener('touchmove', handleMove, { passive: false });
    window.addEventListener('touchend', handleUp);
    return () => {
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('mouseup', handleUp);
      window.removeEventListener('touchmove', handleMove);
      window.removeEventListener('touchend', handleUp);
    };
  }, [onChange, getPercentFromEvent]);

  const pct = clamp(value);

  return (
    <div className="space-y-2.5">
      <div
        ref={trackRef}
        onMouseDown={handlePointerDown}
        onTouchStart={handlePointerDown}
        className="relative h-8 flex items-center cursor-pointer select-none touch-none"
      >
        <div className="absolute inset-x-0 h-1.5 rounded-full bg-border-default" />
        <div
          className={`absolute left-0 h-1.5 rounded-full ${colors.track}`}
          style={{ width: `${pct}%` }}
        />
        {PRESETS.map((p) => (
          <div
            key={p}
            className={`absolute top-1/2 -translate-y-1/2 w-1.5 h-1.5 rounded-full ${pct >= p ? colors.track : 'bg-fg-subtle/30'}`}
            style={{ left: `${p}%` }}
          />
        ))}
        <div
          className={`absolute top-1/2 w-5 h-5 rounded-full ${colors.thumb} shadow-md border-2 border-white`}
          style={{ left: `${pct}%`, transform: 'translate(-50%, -50%)' }}
        />
      </div>

      <div className="flex gap-1.5">
        {PRESETS.map((p) => (
          <button
            key={p}
            type="button"
            onClick={() => onChange(p)}
            className={`flex-1 rounded-md py-1.5 text-[11px] font-semibold transition-all cursor-pointer ${
              pct === p
                ? `${colors.preset} border border-transparent`
                : colors.inactive
            }`}
          >
            %{p}
          </button>
        ))}
      </div>
    </div>
  );
}
