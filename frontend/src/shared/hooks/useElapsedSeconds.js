import { useEffect, useState } from 'react';

export default function useElapsedSeconds(startTimestamp) {
  const [elapsed, setElapsed] = useState(() => (startTimestamp ? Math.floor((Date.now() - startTimestamp) / 1000) : 0));

  useEffect(() => {
    if (!startTimestamp) {
      setElapsed(0);
      return undefined;
    }
    setElapsed(Math.floor((Date.now() - startTimestamp) / 1000));
    const id = setInterval(() => {
      setElapsed(Math.floor((Date.now() - startTimestamp) / 1000));
    }, 1000);
    return () => clearInterval(id);
  }, [startTimestamp]);

  return elapsed;
}
