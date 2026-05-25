import { useState } from 'react';

export default function usePortfolioPageState({ selectedAssetCode, goBack, setSearchParams }) {
  const [pendingAsset, setPendingAsset] = useState(null);
  const [trackedAssetCode, setTrackedAssetCode] = useState(selectedAssetCode);

  const [editTarget, setEditTarget] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [closeTarget, setCloseTarget] = useState(null);
  const [sellTarget, setSellTarget] = useState(null);

  const [onboardingPhase, setOnboardingPhase] = useState('idle');
  const [onboardingName, setOnboardingName] = useState('');

  if (selectedAssetCode !== trackedAssetCode) {
    setTrackedAssetCode(selectedAssetCode);
    if (!selectedAssetCode) setPendingAsset(null);
  }

  const selectAsset = (asset) => {
    if (asset) {
      setPendingAsset(asset);
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.set('asset', asset.assetCode);
        return next;
      }, { replace: false });
    } else {
      setPendingAsset(null);
      goBack();
    }
  };

  const hasActiveDialog = Boolean(deleteTarget || editTarget || sellTarget || closeTarget);

  return {
    pendingAsset,
    setPendingAsset,
    editTarget,
    setEditTarget,
    deleteTarget,
    setDeleteTarget,
    closeTarget,
    setCloseTarget,
    sellTarget,
    setSellTarget,
    onboardingPhase,
    setOnboardingPhase,
    onboardingName,
    setOnboardingName,
    selectAsset,
    hasActiveDialog,
  };
}
