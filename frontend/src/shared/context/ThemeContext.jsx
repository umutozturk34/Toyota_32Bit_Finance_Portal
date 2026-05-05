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

function readGuestPreference() {
    try {
        const stored = localStorage.getItem('finance-theme');
        if (stored === 'light') return 'LIGHT';
        if (stored === 'dark') return 'DARK';
    } catch {
        /* localStorage unavailable */
    }
    return 'DARK';
}

export function ThemeProvider({ children }) {
    const { isAuthenticated } = useAuth();
    const { preferences } = useUserPreferences();
    const updatePreferences = useUpdateUserPreferences();
    const [guestPreference, setGuestPreference] = useState(readGuestPreference);
    const themePreference = isAuthenticated ? (preferences.theme || 'DARK') : guestPreference;

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
        if (isAuthenticated) {
            updatePreferences.mutate({ theme: next });
        } else {
            setGuestPreference(next);
        }
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
