import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { useUserPreferences, useUpdateUserPreferences } from '../hooks/useUserPreferences';

const ThemeContext = createContext({
    theme: 'dark',
    themePreference: 'SYSTEM',
    setThemePreference: () => {},
    toggleTheme: () => {},
    isDark: true,
});

function systemTheme() {
    return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

function resolveTheme(preference) {
    if (preference === 'DARK') return 'dark';
    if (preference === 'LIGHT') return 'light';
    return systemTheme();
}

export function ThemeProvider({ children }) {
    const { preferences } = useUserPreferences();
    const updatePreferences = useUpdateUserPreferences();
    const themePreference = preferences.theme || 'SYSTEM';

    const [systemMatch, setSystemMatch] = useState(systemTheme());

    useEffect(() => {
        const media = window.matchMedia?.('(prefers-color-scheme: light)');
        if (!media) return undefined;
        const handler = (e) => setSystemMatch(e.matches ? 'light' : 'dark');
        media.addEventListener('change', handler);
        return () => media.removeEventListener('change', handler);
    }, []);

    const theme = useMemo(
        () => (themePreference === 'SYSTEM' ? systemMatch : resolveTheme(themePreference)),
        [themePreference, systemMatch]
    );

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        try {
            localStorage.setItem('finance-theme', theme);
            document.cookie = 'finance-theme=' + theme + ';path=/;max-age=31536000;SameSite=Lax';
        } catch {}
    }, [theme]);

    const setThemePreference = (next) => {
        updatePreferences.mutate({ theme: next });
    };

    const toggleTheme = () => {
        const next = theme === 'dark' ? 'LIGHT' : 'DARK';
        setThemePreference(next);
    };

    return (
        <ThemeContext.Provider value={{ theme, themePreference, setThemePreference, toggleTheme, isDark: theme === 'dark' }}>
            {children}
        </ThemeContext.Provider>
    );
}

export function useTheme() {
    const context = useContext(ThemeContext);
    if (!context) throw new Error('useTheme must be used within a ThemeProvider');
    return context;
}
