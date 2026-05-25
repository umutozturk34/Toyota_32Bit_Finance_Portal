import PositionFormModal from './PositionFormModal';
import PositionDeleteDialog from './PositionDeleteDialog';
import CloseDerivativePositionDialog from './CloseDerivativePositionDialog';
import SellPositionDialog from './SellPositionDialog';
import EditDerivativePositionModal from './EditDerivativePositionModal';

export default function PortfolioModalsHost({
  portfolio,
  editTarget,
  setEditTarget,
  closeTarget,
  setCloseTarget,
  sellTarget,
  setSellTarget,
  deleteTarget,
  setDeleteTarget,
  invalidatePortfolio,
}) {
  if (!portfolio) return null;

  return (
    <>
      {editTarget && editTarget.assetType === 'VIOP' && (
        <EditDerivativePositionModal
          portfolioId={portfolio.id}
          position={editTarget}
          onClose={() => { setEditTarget(null); invalidatePortfolio(); }}
        />
      )}

      {editTarget && editTarget.assetType !== 'VIOP' && (
        <PositionFormModal
          mode="edit"
          portfolioId={portfolio.id}
          position={editTarget}
          onClose={() => setEditTarget(null)}
          onComplete={invalidatePortfolio}
        />
      )}

      {closeTarget && (
        <CloseDerivativePositionDialog
          portfolioId={portfolio.id}
          position={closeTarget}
          onClose={() => { setCloseTarget(null); invalidatePortfolio(); }}
        />
      )}

      {sellTarget && (
        <SellPositionDialog
          portfolioId={portfolio.id}
          position={sellTarget}
          onClose={() => setSellTarget(null)}
        />
      )}

      {deleteTarget && (
        <PositionDeleteDialog
          portfolioId={portfolio.id}
          position={deleteTarget}
          onClose={() => setDeleteTarget(null)}
          onComplete={invalidatePortfolio}
        />
      )}
    </>
  );
}
