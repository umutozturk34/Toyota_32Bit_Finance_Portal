import { useCallback, useMemo, useState } from 'react';
import useChartConfig from './useChartConfig';
import { randomId } from '../../../shared/utils/id';

const genId = () => `f-${randomId()}`;

export default function useFibonacci(assetType, assetCode, range, persistEnabled = true) {
    const { config, setField } = useChartConfig(assetType, assetCode, range, persistEnabled);
    const fibTools = useMemo(
        () => (Array.isArray(config?.fibTools) ? config.fibTools : []),
        [config?.fibTools],
    );
    const [activeFibTool, setActiveFibTool] = useState(null);

    const addFibTool = useCallback((tool) => {
        setField('fibTools', (prev) => [
            ...(Array.isArray(prev) ? prev : []),
            { ...tool, id: genId() },
        ]);
    }, [setField]);

    const removeFibTool = useCallback((id) => {
        setField('fibTools', (prev) => (Array.isArray(prev) ? prev : []).filter((f) => f.id !== id));
    }, [setField]);

    const clearFibTools = useCallback(() => {
        setField('fibTools', []);
        setActiveFibTool(null);
    }, [setField]);

    const selectFibTool = useCallback((type) => {
        setActiveFibTool((prev) => (prev === type ? null : type));
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
