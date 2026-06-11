import { useEffect } from 'react';

// Lets a horizontally-scrollable row be scrolled with a vertical mouse wheel. With a plain mouse (no
// horizontal wheel/trackpad), a wheel over an overflow-x row otherwise just scrolls the page vertically;
// this translates wheel deltaY into scrollLeft so the row pans left/right. Trackpad horizontal gestures
// (deltaX) are left to the browser, and once the row hits an edge in the wheel direction the event is
// released so the page can resume scrolling (no scroll trap). The listener is non-passive so the page
// scroll can be prevented while the row still has room.
export default function useWheelToHorizontal(ref) {
  useEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    const onWheel = (e) => {
      if (el.scrollWidth <= el.clientWidth) return;
      if (Math.abs(e.deltaX) > Math.abs(e.deltaY)) return;
      const atStart = el.scrollLeft <= 0;
      const atEnd = el.scrollLeft + el.clientWidth >= el.scrollWidth - 1;
      if ((e.deltaY < 0 && atStart) || (e.deltaY > 0 && atEnd)) return;
      e.preventDefault();
      el.scrollLeft += e.deltaY;
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, [ref]);
}
