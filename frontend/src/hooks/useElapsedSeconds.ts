import { useEffect, useRef, useState } from 'react';

/** Segundos decorridos desde que `active` virou `true`; zera quando volta a `false`. */
export function useElapsedSeconds(active: boolean): number {
  const [elapsed, setElapsed] = useState(0);
  const startRef = useRef<number | null>(null);

  useEffect(() => {
    if (!active) {
      startRef.current = null;
      setElapsed(0);
      return;
    }

    startRef.current = Date.now();
    setElapsed(0);
    const intervalId = window.setInterval(() => {
      const start = startRef.current;
      if (start !== null) {
        setElapsed(Math.floor((Date.now() - start) / 1000));
      }
    }, 1000);

    return () => window.clearInterval(intervalId);
  }, [active]);

  return elapsed;
}
