import { useEffect, useRef, useState } from 'react';

export default function useProcessingAnimation() {
  const [processingStep, setProcessingStep] = useState(-1);
  const stepTimers = useRef([]);

  useEffect(() => () => stepTimers.current.forEach(clearTimeout), []);

  const runAnimation = (steps) => new Promise((resolve) => {
    let elapsed = 0;
    steps.forEach((step, idx) => {
      const timer = setTimeout(() => setProcessingStep(idx), elapsed);
      stepTimers.current.push(timer);
      elapsed += step.duration;
    });
    const finalTimer = setTimeout(resolve, elapsed);
    stepTimers.current.push(finalTimer);
  });

  const reset = () => setProcessingStep(-1);

  return { processingStep, setProcessingStep, runAnimation, reset };
}
