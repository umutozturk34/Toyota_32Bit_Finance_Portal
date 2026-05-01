import { useState, useCallback } from 'react';

export default function useProcessingAnimation() {
  const [processingStep, setProcessingStep] = useState(-1);

  const runAnimation = useCallback((steps) => new Promise((resolve) => {
    setProcessingStep(0);
    let elapsed = 0;
    steps.forEach((step, idx) => {
      setTimeout(() => setProcessingStep(idx), elapsed);
      elapsed += step.duration;
    });
    setTimeout(() => {
      setProcessingStep(steps.length);
      resolve();
    }, elapsed);
  }), []);

  const reset = useCallback(() => setProcessingStep(-1), []);

  return { processingStep, runAnimation, reset };
}
