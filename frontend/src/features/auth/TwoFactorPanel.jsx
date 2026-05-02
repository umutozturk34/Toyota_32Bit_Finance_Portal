import { useEffect, useState } from 'react';
import axios from 'axios';
import { Shield, Settings as SettingsIcon, Loader2, AlertTriangle, CheckCircle } from 'lucide-react';
import keycloak, { getToken } from './keycloak';

const KEYCLOAK_ACCOUNT_URL = 'http://localhost:8180/realms/finance-realm/account/credentials';

export default function TwoFactorPanel() {
    const [status, setStatus] = useState({ loading: true, configured: false });

    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                const token = await getToken();
                const response = await axios.get(KEYCLOAK_ACCOUNT_URL, {
                    headers: { Authorization: `Bearer ${token}` },
                });
                const hasTotp = response.data.some((cred) => cred.type === 'otp');
                if (!cancelled) setStatus({ loading: false, configured: hasTotp });
            } catch {
                if (!cancelled) setStatus({ loading: false, configured: false });
            }
        })();
        return () => { cancelled = true; };
    }, []);

    const handleSetup = () => {
        keycloak.login({
            action: 'CONFIGURE_TOTP',
            redirectUri: window.location.origin + '/',
        });
    };

    if (status.loading) {
        return (
            <div className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-xs text-fg-muted">
                <Loader2 className="h-3.5 w-3.5 animate-spin text-accent" />
                Durum kontrol ediliyor...
            </div>
        );
    }

    const enabled = status.configured;

    return (
        <div className="space-y-2.5">
            <div className={`flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium ${
                enabled
                    ? 'bg-success/10 text-success border border-success/30'
                    : 'bg-warning/10 text-warning border border-warning/30'
            }`}>
                {enabled
                    ? <CheckCircle className="h-3.5 w-3.5 shrink-0" />
                    : <AlertTriangle className="h-3.5 w-3.5 shrink-0" />}
                <span>{enabled ? '2FA aktif' : '2FA kapalı'}</span>
            </div>
            <p className="text-[11px] text-fg-muted leading-relaxed px-1">
                {enabled
                    ? 'Hesabın iki adımlı doğrulama ile korunuyor.'
                    : 'Google/Microsoft Authenticator ile hesabını ek güvenlik katmanıyla koru.'}
            </p>
            <button
                onClick={handleSetup}
                className="w-full flex items-center justify-center gap-2 rounded-lg py-2.5 text-xs font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
            >
                {enabled
                    ? <><SettingsIcon className="h-3.5 w-3.5" /> 2FA'yı Yönet</>
                    : <><Shield className="h-3.5 w-3.5" /> 2FA'yı Kur</>}
            </button>
            <p className="text-[10px] text-fg-subtle leading-relaxed px-1">
                Keycloak Account Management'a yönlendirileceksin.
            </p>
        </div>
    );
}
