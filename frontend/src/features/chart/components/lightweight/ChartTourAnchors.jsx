import React from 'react';

// Visually-hidden buttons the onboarding tour drives programmatically to open/close the drawing sidebar.
// aria-hidden + tabIndex -1 + clip keep them out of the normal a11y/focus flow; only the tour clicks them.
// Purely presentational — they fire the store setters handed down and own no chart refs.
const HIDDEN_STYLE = { position: 'absolute', width: 1, height: 1, padding: 0, margin: -1, overflow: 'hidden', clip: 'rect(0,0,0,0)', whiteSpace: 'nowrap', border: 0 };

const ChartTourAnchors = ({ setSidebarOpen, setActiveTab }) => (
    <>
        <button
            type="button"
            data-tour="chart-drawing-open"
            aria-hidden="true"
            tabIndex={-1}
            onClick={() => { setSidebarOpen(true); setActiveTab('drawings'); }}
            style={HIDDEN_STYLE}
        />
        <button
            type="button"
            data-tour-close="chart-drawing"
            aria-hidden="true"
            tabIndex={-1}
            onClick={() => { setSidebarOpen(false); }}
            style={HIDDEN_STYLE}
        />
    </>
);

export default ChartTourAnchors;
