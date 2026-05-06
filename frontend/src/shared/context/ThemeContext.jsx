import React, { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
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
        if (stored === 'light') return { value: 'LIGHT', exists: true };
        if (stored === 'dark') return { value: 'DARK', exists: true };
    } catch {
        /* localStorage unavailable */
    }
    return { value: 'DARK', exists: false };
}

function BootSplash() {
    return (
        <div
            aria-hidden="true"
            style={{
                position: 'fixed',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'var(--color-bg-base)',
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
    const { preferences, isLoading: prefsLoading, isFetched: prefsFetched } = useUserPreferences();
    const updatePreferences = useUpdateUserPreferences();
    const [{ value: initialPref, exists: initialFromLocal }] = useState(readStoredPreference);
    const [storedPreference, setStoredPreference] = useState(initialPref);
    const localOverrideRef = useRef(initialFromLocal);

    useEffect(() => {
        if (localOverrideRef.current) return;
        if (!preferences.theme) return;
        if (preferences.theme === storedPreference) return;
        setStoredPreference(preferences.theme);
    }, [preferences.theme, storedPreference]);

    const themePreference = storedPreference;
    const theme = useMemo(() => resolveTheme(themePreference), [themePreference]);

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        try {
            localStorage.setItem('finance-theme', theme);
            document.cookie = 'finance-theme=' + theme + ';path=/;max-age=31536000;SameSite=Lax';
        } catch {
            /* localStorage unavailable */
        }
    }, [theme]);

    const setThemePreference = (next) => {
        localOverrideRef.current = true;
        setStoredPreference(next);
        if (isAuthenticated) {
            updatePreferences.mutate({ theme: next });
        }
    };

    const toggleTheme = () => {
        const next = theme === 'dark' ? 'LIGHT' : 'DARK';
        setThemePreference(next);
    };

    const blocking = authLoading || (isAuthenticated && !prefsFetched && prefsLoading);

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
