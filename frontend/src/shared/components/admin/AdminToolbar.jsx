import { Download, LineChart, Wrench } from 'lucide-react';
import Spinner from '../feedback/Spinner';

export default function AdminToolbar({ actions, updating, disabled = false }) {
    const defaultIcons = {
        snapshot: <Download className="h-4 w-4" />,
        candles: <LineChart className="h-4 w-4" />,
        full: <Wrench className="h-4 w-4" />,
    };

    return (
        <>
            {actions.map(({ key, label, title, handler, icon }) => (
                <button
                    key={key}
                    onClick={handler}
                    disabled={updating[key] || disabled}
                    title={title}
                    className="flex items-center gap-2 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 min-h-10 text-sm text-accent-bright transition-colors duration-150 hover:bg-accent/20 disabled:opacity-50"
                >
                    {updating[key] ? (
                        <Spinner size="sm" tone="inherit" />
                    ) : (
                        icon || defaultIcons[key] || <Wrench className="h-4 w-4" />
                    )}
                    {label}
                </button>
            ))}
        </>
    );
}
