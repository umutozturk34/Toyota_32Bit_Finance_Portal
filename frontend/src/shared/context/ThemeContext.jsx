import { useLayoutEffect, useMemo, useState } from 'react';
import { flushSync } from 'react-dom';
import { useUserPreferences, useUpdateUserPreferences } from '../hooks/useUserPreferences';
import { useAuth } from '../../features/auth/useAuth';
import { ThemeContext } from './useTheme';

const THEME_KEY = 'finance-theme';
const FADE_MS = 450;
const RIPPLE_MS = 520;

// Last pointer position, captured globally so the theme switch can ripple out from wherever the user clicked (the
// sidebar toggle, the settings control, …). Falls back to the bottom-left corner (near the sidebar) when unknown.
let lastPointer = null;
if (typeof window !== 'undefined') {
    window.addEventListener(
        'pointerdown',
        (e) => { lastPointer = { x: e.clientX, y: e.clientY }; },
        { capture: true, passive: true },
    );
}

function runThemeTransition(nextTheme, apply) {
    if (typeof document === 'undefined') {
        apply();
        return;
    }
    const root = document.documentElement;
    const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    const commit = () => {
        root.setAttribute('data-theme', nextTheme);
        apply();
    };

    // Reduced motion → instant. No View Transitions API → a brief CSS cross-fade rather than a hard cut.
    if (reducedMotion) {
        commit();
        return;
    }
    if (typeof document.startViewTransition !== 'function') {
        root.classList.add('theme-switching');
        commit();
        window.setTimeout(() => root.classList.remove('theme-switching'), FADE_MS);
        return;
    }

    // Circular reveal: the NEW theme circles in over the OLD from the click point. View Transitions snapshots the
    // viewport ONCE and animates only the new layer's clip-path on the compositor, so it stays smooth even on the
    // heavy in-app DOM (charts/grids) — a per-element CSS transition would repaint the whole tree and jank.
    const { innerWidth: w, innerHeight: h } = window;
    const x = lastPointer?.x ?? 48;
    const y = lastPointer?.y ?? h - 48;
    const endRadius = Math.hypot(Math.max(x, w - x), Math.max(y, h - y));
    root.classList.add('theme-ripple');
    const transition = document.startViewTransition(() => flushSync(commit));
    transition.ready.then(() => {
        root.animate(
            { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${endRadius}px at ${x}px ${y}px)`] },
            { duration: RIPPLE_MS, easing: 'cubic-bezier(0.4, 0, 0.2, 1)', pseudoElement: '::view-transition-new(root)' },
        );
    }).catch(() => { /* a superseded transition is fine */ });
    transition.finished.finally(() => root.classList.remove('theme-ripple'));
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
        const root = document.documentElement;
        root.setAttribute('data-theme', theme);
        // Inline color-scheme keeps Android Chrome's force-dark from re-applying
        // when the user explicitly switches themes after first paint.
        root.style.colorScheme = theme;
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
