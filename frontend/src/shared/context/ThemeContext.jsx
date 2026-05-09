import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { useUserPreferences, useUpdateUserPreferences } from '../hooks/useUserPreferences';
import { useAuth } from '../../features/auth/AuthContext';

const ThemeContext = createContext({
    theme: 'dark',
    themePreference: 'DARK',
    setThemePreference: () => {},
    toggleTheme: () => {},
    isDark: true,
});

function resolveTheme(preference) {
    return preference === 'LIGHT' ? 'light' : 'dark';
}

function readStoredPreference() {
    try {
        const stored = localStorage.getItem('finance-theme');
        if (stored === 'light') return 'LIGHT';
        if (stored === 'dark') return 'DARK';
    } catch {
        /* localStorage unavailable */
    }
    return 'DARK';
}

function BootSplash() {
    const stored = (() => {
        try { return localStorage.getItem('finance-theme') === 'light' ? 'light' : 'dark'; }
        catch { return 'dark'; }
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

function persistTheme(theme) {
    try {
        localStorage.setItem('finance-theme', theme);
        document.cookie = 'finance-theme=' + theme + ';path=/;max-age=31536000;SameSite=Lax';
    } catch {
        /* storage unavailable */
    }
}

export function ThemeProvider({ children }) {
    const { isAuthenticated, loading: authLoading } = useAuth();
    const { preferences, hasResolvedPreferences, isFetched: prefsFetched } = useUserPreferences();
    const updatePreferences = useUpdateUserPreferences();
    const [storedPreference, setStoredPreferenceState] = useState(readStoredPreference);

    useEffect(() => {
        if (!isAuthenticated) return;
        if (!hasResolvedPreferences) return;
        if (!preferences.theme) return;
        if (preferences.theme === storedPreference) return;
        setStoredPreferenceState(preferences.theme);
        persistTheme(resolveTheme(preferences.theme));
    }, [isAuthenticated, hasResolvedPreferences, preferences.theme, storedPreference]);

    const themePreference = storedPreference;
    const theme = useMemo(() => resolveTheme(themePreference), [themePreference]);

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
    }, [theme]);

    const setThemePreference = (next) => {
        const previous = storedPreference;
        setStoredPreferenceState(next);
        persistTheme(resolveTheme(next));
        if (isAuthenticated) {
            updatePreferences.mutate({ theme: next }, {
                onError: () => {
                    setStoredPreferenceState(previous);
                    persistTheme(resolveTheme(previous));
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

export function useTheme() {
    const context = useContext(ThemeContext);
    if (!context) throw new Error('useTheme must be used within a ThemeProvider');
    return context;
}
