import { useEffect, useState } from 'react';

const computeElapsed = (startTimestamp) =>
  startTimestamp ? Math.floor((Date.now() - startTimestamp) / 1000) : 0;

export default function useElapsedSeconds(startTimestamp) {
  const [elapsed, setElapsed] = useState(() => computeElapsed(startTimestamp));
  const [trackedStart, setTrackedStart] = useState(startTimestamp);

  if (startTimestamp !== trackedStart) {
    setTrackedStart(startTimestamp);
    setElapsed(computeElapsed(startTimestamp));
  }

  useEffect(() => {
    if (!startTimestamp) return undefined;
    const id = setInterval(() => {
      setElapsed(computeElapsed(startTimestamp));
    }, 1000);
    return () => clearInterval(id);
  }, [startTimestamp]);

  return elapsed;
}
