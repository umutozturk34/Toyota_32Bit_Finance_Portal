import React from 'react';
import DataWindowPanel from '../DataWindowPanel';

// The right-hand analytics half of the Lens: the full DataWindowPanel cockpit plus any host-supplied sidebar
// slot. Rendered only when the Lens is open and there's something to show. Purely presentational — owns no
// chart refs and only reads the candles/crosshair handed down.
const ChartLensPanel = ({ candles, crosshairData, assetType, sidebar, isFullscreen }) => {
    const hasCandles = candles?.length > 0;
    return (
        <div className={`w-full border-t border-border-default overscroll-contain max-h-[60dvh] overflow-y-auto scrollbar-thin xl:max-h-none xl:w-[340px] xl:shrink-0 xl:border-t-0 xl:border-l xl:overflow-y-auto ${isFullscreen ? '' : 'xl:absolute xl:inset-y-0 xl:right-0'}`}>
            {hasCandles && (
                <DataWindowPanel candles={candles} hover={crosshairData} assetType={assetType} variant="analytics" />
            )}
            {sidebar && (
                <div className={`p-3 sm:p-4 space-y-3 ${hasCandles ? 'border-t border-border-default' : ''}`}>
                    {sidebar}
                </div>
            )}
        </div>
    );
};

export default ChartLensPanel;
