import React, { createContext, useContext, useState, useEffect } from 'react';
const ThemeContext = createContext({
    theme: 'dark',
    toggleTheme: () => { },
    isDark: true,
});
export function ThemeProvider({ children }) {
    const [theme, setTheme] = useState(() => {
        try {
            const saved = localStorage.getItem('finance-theme');
            if (saved === 'light' || saved === 'dark') return saved;
        } catch {  }
        return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    });
    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        try { localStorage.setItem('finance-theme', theme); } catch {  }
    }, [theme]);
    const toggleTheme = () => setTheme(prev => (prev === 'dark' ? 'light' : 'dark'));
    return (
        <ThemeContext.Provider value={{ theme, toggleTheme, isDark: theme === 'dark' }}>
            {children}
        </ThemeContext.Provider>
    );
}
export function useTheme() {
    const context = useContext(ThemeContext);
    if (!context) throw new Error('useTheme must be used within a ThemeProvider');
    return context;
}
