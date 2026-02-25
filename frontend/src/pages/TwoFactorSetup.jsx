import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import {
    Shield,
    Smartphone,
    QrCode,
    Key,
    Copy,
    Check,
    Loader2,
    AlertTriangle,
    CheckCircle,
    ExternalLink,
    Settings,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import axios from 'axios';
import { getToken } from '../services/keycloak';
const TwoFactorSetup = () => {
    const { user } = useAuth();
    const { isDark } = useTheme();
    const [totpStatus, setTotpStatus] = useState(null);
    const [loading, setLoading] = useState(true);
    const [setupUrl, setSetupUrl] = useState('');
    const [message, setMessage] = useState('');
    useEffect(() => {
        fetchTotpStatus();
    }, []);
    const fetchTotpStatus = async () => {
        try {
            const token = await getToken();
            const realm = 'finance-realm';
            const response = await axios.get(
                `http://localhost:8180/realms/${realm}/account/credentials`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                        'Content-Type': 'application/json',
                    },
                }
            );
            const hasTotp = response.data.some((cred) => cred.type === 'otp');
            setTotpStatus({ configured: hasTotp });
        } catch (error) {
            console.error('Failed to fetch TOTP status:', error);
            setTotpStatus({ configured: false });
        } finally {
            setLoading(false);
        }
    };
    const handleSetup2FA = () => {
        const realm = 'finance-realm';
        const accountUrl = `http://localhost:8180/realms/${realm}/account/#/security/signingin`;
        window.location.href = accountUrl;
    };
        if (loading) {
        return (
            <div className="flex min-h-[60vh] items-center justify-center">
                <motion.div
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="flex flex-col items-center gap-4"
                >
                    <Loader2 className="h-8 w-8 animate-spin text-accent" />
                    <span className="text-sm text-fg-muted tracking-wide">Loading…</span>
                </motion.div>
            </div>
        );
    }
        const isEnabled = totpStatus?.configured;
    const steps = [
        { icon: Key, text: 'Click "Setup 2FA" button below' },
        { icon: QrCode, text: 'Scan the QR code with Google Authenticator app' },
        { icon: Copy, text: 'Enter the 6-digit code to verify' },
        { icon: CheckCircle, text: "From now on, you'll need the code when logging in" },
    ];
    const apps = [
        { icon: Smartphone, name: 'Google Authenticator' },
        { icon: Shield, name: 'Microsoft Authenticator' },
        { icon: Key, name: 'Authy' },
    ];
    return (
        <div className="flex min-h-[60vh] items-center justify-center px-4 py-12">
            <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
                className="relative w-full max-w-lg rounded-xl border border-border-default bg-bg-elevated overflow-hidden card-hover"
            >
                {}
                {isDark && (
                    <span className="pointer-events-none absolute -top-20 left-1/2 -translate-x-1/2 w-60 h-40 rounded-full bg-accent/[0.06] blur-[80px]" aria-hidden="true" />
                )}
                {}
                <div className="relative border-b border-border-default px-6 py-6 text-center">
                    <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3, delay: 0.05 }}
                    >
                        <span className="flex items-center justify-center w-12 h-12 rounded-xl bg-accent/10 text-accent mx-auto mb-3">
                            <Shield className="h-6 w-6" />
                        </span>
                    </motion.div>
                    <motion.h1
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3, delay: 0.1 }}
                        className="text-2xl font-bold text-fg"
                    >
                        Two-Factor Authentication
                    </motion.h1>
                    <motion.p
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3, delay: 0.15 }}
                        className="mt-2 text-sm text-fg-muted"
                    >
                        Add an extra layer of security to your account
                    </motion.p>
                </div>
                <div className="space-y-5 p-6">
                    {}
                    <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3, delay: 0.2 }}
                        className="flex flex-col items-center gap-3"
                    >
                        <span
                            className={`inline-flex items-center gap-2 rounded-full px-4 py-2 text-sm font-medium ${
                                isEnabled
                                    ? 'bg-success/15 text-success border border-success/30'
                                    : 'bg-warning/15 text-warning border border-warning/30'
                            }`}
                        >
                            {isEnabled ? (
                                <>
                                    <CheckCircle className="h-4 w-4" /> Enabled
                                </>
                            ) : (
                                <>
                                    <AlertTriangle className="h-4 w-4" /> Disabled
                                </>
                            )}
                        </span>
                        <p className="text-center text-sm text-fg-muted leading-relaxed">
                            {isEnabled
                                ? 'Two-factor authentication is currently enabled on your account.'
                                : 'Two-factor authentication is not enabled. Protect your account by enabling 2FA.'}
                        </p>
                    </motion.div>
                    {}
                    <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3, delay: 0.25 }}
                        className="rounded-lg border border-border-default bg-bg-base p-5"
                    >
                        <h3 className="mb-4 text-sm font-semibold text-fg">How it works:</h3>
                        <ol className="space-y-3">
                            {steps.map((step, i) => (
                                <li key={i} className="flex items-start gap-3">
                                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md border border-border-default text-fg-muted text-xs font-bold">
                                        {i + 1}
                                    </span>
                                    <div className="flex items-center gap-2 pt-0.5 text-sm text-fg-muted">
                                        <step.icon className="h-3.5 w-3.5 shrink-0 text-fg-subtle" />
                                        {step.text}
                                    </div>
                                </li>
                            ))}
                        </ol>
                    </motion.div>
                    {}
                    <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3, delay: 0.3 }}
                    >
                        <h3 className="mb-3 text-sm font-semibold text-fg">Compatible Apps:</h3>
                        <div className="grid grid-cols-3 gap-3">
                            {apps.map((app) => (
                                <div
                                    key={app.name}
                                    className="group flex flex-col items-center gap-2 rounded-md border border-border-default bg-bg-base p-3 hover:bg-surface transition-colors duration-150"
                                >
                                    <app.icon className="h-5 w-5 text-fg-subtle group-hover:text-accent transition-colors duration-150" />
                                    <span className="text-center text-xs text-fg-muted leading-tight">
                                        {app.name}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </motion.div>
                    {}
                    <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3, delay: 0.35 }}
                    >
                        <button
                            onClick={handleSetup2FA}
                            className="flex w-full items-center justify-center gap-2.5 rounded-lg py-3 px-6 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all duration-150 active:scale-[0.98]"
                            style={{
                                boxShadow: isDark
                                    ? '0 0 0 1px rgba(94,106,210,0.5), 0 2px 12px rgba(94,106,210,0.25), inset 0 1px 0 0 rgba(255,255,255,0.1)'
                                    : undefined,
                            }}
                        >
                            {isEnabled ? (
                                <>
                                    <Settings className="h-4 w-4" /> Manage 2FA
                                </>
                            ) : (
                                <>
                                    <ExternalLink className="h-4 w-4" /> Setup 2FA
                                </>
                            )}
                        </button>
                    </motion.div>
                    {}
                    <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3, delay: 0.4 }}
                        className="rounded-lg border border-border-default bg-bg-base p-4"
                    >
                        <p className="text-xs text-fg-subtle leading-relaxed">
                            <span className="font-semibold text-fg-muted">Note:</span>{' '}
                            You'll be redirected to Keycloak Account Management where you can
                            securely set up your two-factor authentication.
                        </p>
                    </motion.div>
                </div>
            </motion.div>
        </div>
    );
};
export default TwoFactorSetup;
