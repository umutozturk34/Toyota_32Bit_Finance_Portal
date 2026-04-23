import { useState, useCallback, useMemo } from 'react';
import useProcessingAnimation from './useProcessingAnimation';

export default function useTransactionFlow({
  isFractional,
  initialInputMode = 'quantity',
  maxAmount = 0,
  maxQuantity = 0,
}) {
  const [inputMode, setInputMode] = useState(initialInputMode);
  const [amountTry, setAmountTry] = useState('');
  const [quantity, setQuantity] = useState('');
  const [sliderPercent, setSliderPercent] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const { processingStep, runAnimation, reset: resetProcessing } = useProcessingAnimation();

  const isAmountMode = isFractional && inputMode === 'amount';

  const handleSliderChange = useCallback((pct) => {
    setSliderPercent(pct);
    setError(null);
    if (isAmountMode) {
      if (maxAmount <= 0) return;
      const amount = (maxAmount * pct) / 100;
      setAmountTry(pct === 0 ? '' : String(Math.floor(amount * 100) / 100));
    } else {
      if (maxQuantity <= 0) return;
      const qty = isFractional ? (maxQuantity * pct) / 100 : Math.floor((maxQuantity * pct) / 100);
      setQuantity(pct === 0 ? '' : String(qty));
    }
  }, [isAmountMode, isFractional, maxAmount, maxQuantity]);

  const syncSliderFromInput = useCallback((val) => {
    if (isAmountMode) {
      if (maxAmount <= 0) { setSliderPercent(0); return; }
      const pct = Math.min(100, Math.round((Number(val) / maxAmount) * 100));
      setSliderPercent(pct > 0 ? pct : 0);
    } else {
      if (maxQuantity <= 0) { setSliderPercent(0); return; }
      const pct = Math.min(100, Math.round((Number(val) / maxQuantity) * 100));
      setSliderPercent(pct > 0 ? pct : 0);
    }
  }, [isAmountMode, maxAmount, maxQuantity]);

  const handleModeSwitch = (mode) => {
    setInputMode(mode);
    setAmountTry('');
    setQuantity('');
    setSliderPercent(0);
    setError(null);
  };

  const execute = async ({ executor, processingSteps, onSuccess: onSuccessCb, defaultErrorMessage }) => {
    setConfirming(false);
    setLoading(true);
    setError(null);
    try {
      await Promise.all([executor(), runAnimation(processingSteps)]);
      setSuccess(true);
      setTimeout(() => onSuccessCb?.(), 1800);
    } catch (err) {
      resetProcessing();
      setError(err.response?.data?.message || defaultErrorMessage);
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setAmountTry('');
    setQuantity('');
    setSliderPercent(0);
    setError(null);
  };

  return useMemo(() => ({
    inputMode, amountTry, quantity, sliderPercent,
    loading, error, success, confirming, processingStep,
    isAmountMode,
    setAmountTry, setQuantity, setError, setConfirming,
    handleSliderChange, syncSliderFromInput, handleModeSwitch,
    execute, reset,
  }), [
    inputMode, amountTry, quantity, sliderPercent,
    loading, error, success, confirming, processingStep,
    isAmountMode, handleSliderChange, syncSliderFromInput,
  ]);
}
