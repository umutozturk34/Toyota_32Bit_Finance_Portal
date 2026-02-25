import { useState, useCallback } from 'react';
export default function useFibonacci() {
    const [fibTools, setFibTools] = useState([]);
    const [activeFibTool, setActiveFibTool] = useState(null);
    const addFibTool = useCallback((tool) => {
        setFibTools(prev => [
            ...prev,
            { ...tool, id: `f-${Date.now()}-${Math.random().toString(36).slice(2, 6)}` },
        ]);
    }, []);
    const removeFibTool = useCallback((id) => {
        setFibTools(prev => prev.filter(f => f.id !== id));
    }, []);
    const clearFibTools = useCallback(() => {
        setFibTools([]);
        setActiveFibTool(null);
    }, []);
    const selectFibTool = useCallback((type) => {
        setActiveFibTool(prev => (prev === type ? null : type));
    }, []);
    const cancelFibTool = useCallback(() => {
        setActiveFibTool(null);
    }, []);
    return {
        fibTools,
        activeFibTool,
        addFibTool,
        removeFibTool,
        clearFibTools,
        selectFibTool,
        cancelFibTool,
    };
}
