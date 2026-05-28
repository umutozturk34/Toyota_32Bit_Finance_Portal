import { useLayoutEffect, useMemo, useState } from 'react';
import { flushSync } from 'react-dom';
import { useUserPreferences, useUpdateUserPreferences } from '../hooks/useUserPreferences';
import { useAuth } from '../../features/auth/useAuth';
import { ThemeContext } from './useTheme';

const THEME_KEY = 'finance-theme';
const FADE_MS = 450;

function runThemeTransition(nextTheme, apply) {
    if (typeof document === 'undefined') {
        apply();
        return;
    }
    const root = document.documentElement;
    const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    const onHomepage = root.dataset.themeFade === '1';
    const commit = () => {
        root.setAttribute('data-theme', nextTheme);
        apply();
    };

    // In-app (heavy DOM) or reduced motion → instant, no jank
    if (!onHomepage || reducedMotion) {
        commit();
        return;
    }

    // Homepage (light DOM) → rich View Transitions cross-fade
    if (typeof document.startViewTransition === 'function') {
        document.startViewTransition(() => flushSync(commit));
        return;
    }

    // Homepage fallback (no View Transitions API) → CSS fade
    root.classList.add('theme-switching');
    commit();
    window.setTimeout(() => root.classList.remove('theme-switching'), FADE_MS);
}

function resolveTheme(preference) {
    return preference === 'LIGHT' ? 'light' : 'dark';
}

function readSystemPreference() {
    try {
        if (typeof window !== 'undefined' && window.matchMedia) {
            return window.matchMedia('(prefers-color-scheme: light)').matches ? 'LIGHT' : 'DARK';
        }
    } catch { void 0; }
    return 'DARK';
}

function readStoredPreference() {
    try {
        const stored = localStorage.getItem(THEME_KEY);
        if (stored === 'light') return 'LIGHT';
        if (stored === 'dark') return 'DARK';
    } catch { void 0; }
    return null;
}

function hasStoredPreference() {
    return readStoredPreference() !== null;
}

function persistTheme(theme) {
    try {
        localStorage.setItem(THEME_KEY, theme);
        document.cookie = `${THEME_KEY}=${theme};path=/;max-age=31536000;SameSite=Lax`;
    } catch { void 0; }
}

function BootSplash() {
    const stored = (() => {
        try {
            const raw = localStorage.getItem(THEME_KEY);
            if (raw === 'light' || raw === 'dark') return raw;
        } catch { /* noop */ }
        try {
            if (window.matchMedia('(prefers-color-scheme: light)').matches) return 'light';
        } catch { /* noop */ }
        return 'dark';
    })();
    const bg = stored === 'light' ? '#F2F6FB' : '#0e0e14';
    return (
        <div
            aria-hidden="true"
            data-theme={stored}
            style={{
                position: 'fixed',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: bg,
                zIndex: 9999,
            }}
        >
            <div
                style={{
                    width: 36,
                    height: 36,
                    borderRadius: '50%',
                    border: '2px solid rgba(99,102,241,0.18)',
                    borderTopColor: 'var(--color-accent)',
                    animation: 'theme-boot-spin 0.9s linear infinite',
                }}
            />
            <style>{`@keyframes theme-boot-spin { to { transform: rotate(360deg); } }`}</style>
        </div>
    );
}

export function ThemeProvider({ children }) {
    const { isAuthenticated, loading: authLoading } = useAuth();
    const { preferences, hasResolvedPreferences, isFetched: prefsFetched } = useUserPreferences();
    const updatePreferences = useUpdateUserPreferences();
    const [storedPreference, setStoredPreferenceState] = useState(
        () => readStoredPreference() ?? readSystemPreference(),
    );
    const [trackedServerTheme, setTrackedServerTheme] = useState(null);

    const serverTheme = isAuthenticated && hasResolvedPreferences ? preferences.theme : null;
    if (serverTheme && serverTheme !== trackedServerTheme) {
        setTrackedServerTheme(serverTheme);
        if (serverTheme !== storedPreference) {
            setStoredPreferenceState(serverTheme);
            persistTheme(resolveTheme(serverTheme));
        } else if (!hasStoredPreference()) {
            persistTheme(resolveTheme(serverTheme));
        }
    }

    const themePreference = storedPreference;
    const theme = useMemo(() => resolveTheme(themePreference), [themePreference]);

    useLayoutEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
    }, [theme]);

    const setThemePreference = (next) => {
        const previous = storedPreference;
        runThemeTransition(resolveTheme(next), () => {
            setStoredPreferenceState(next);
            persistTheme(resolveTheme(next));
        });
        if (isAuthenticated) {
            updatePreferences.mutate({ theme: next }, {
                onError: () => {
                    runThemeTransition(resolveTheme(previous), () => {
                        setStoredPreferenceState(previous);
                        persistTheme(resolveTheme(previous));
                    });
                },
            });
        }
    };

    const toggleTheme = () => {
        const next = theme === 'dark' ? 'LIGHT' : 'DARK';
        setThemePreference(next);
    };

    const blocking = authLoading || (isAuthenticated && !prefsFetched);

    return (
        <ThemeContext.Provider value={{ theme, themePreference, setThemePreference, toggleTheme, isDark: theme === 'dark' }}>
            {blocking ? <BootSplash /> : children}
        </ThemeContext.Provider>
    );
}
