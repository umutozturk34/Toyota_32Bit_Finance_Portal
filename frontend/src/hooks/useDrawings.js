import { useState, useCallback } from 'react';
export default function useDrawings() {
    const [drawings, setDrawings] = useState([]);
    const [activeTool, setActiveTool] = useState(null);
    const addDrawing = useCallback((drawing) => {
        setDrawings(prev => [...prev, { ...drawing, id: `d-${Date.now()}-${Math.random().toString(36).slice(2, 6)}` }]);
    }, []);
    const removeDrawing = useCallback((id) => {
        setDrawings(prev => prev.filter(d => d.id !== id));
    }, []);
    const undoDrawing = useCallback(() => {
        setDrawings(prev => prev.slice(0, -1));
    }, []);
    const clearDrawings = useCallback(() => {
        setDrawings([]);
        setActiveTool(null);
    }, []);
    const selectTool = useCallback((tool) => {
        setActiveTool(prev => (prev === tool ? null : tool));
    }, []);
    const cancelTool = useCallback(() => {
        setActiveTool(null);
    }, []);
    return {
        drawings,
        activeTool,
        addDrawing,
        removeDrawing,
        undoDrawing,
        clearDrawings,
        selectTool,
        cancelTool,
    };
}
