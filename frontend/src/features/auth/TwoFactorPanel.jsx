import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Shield, ShieldOff, RefreshCw, Loader2, AlertTriangle, CheckCircle } from 'lucide-react';
import keycloak from './keycloak';
import { userCredentialService } from '../../shared/services/userCredentialService';
import { toast } from '../../shared/components/Toast';
import ConfirmDialog from '../../shared/components/ConfirmDialog';

const STATUS_KEY = ['twoFactor', 'status'];

function useTwoFactorStatus() {
    return useQuery({
        queryKey: STATUS_KEY,
        queryFn: userCredentialService.getTwoFactorStatus,
        staleTime: 60_000,
        refetchOnWindowFocus: false,
    });
}

function useDisableTwoFactor() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: userCredentialService.disableTwoFactor,
        onSuccess: () => queryClient.invalidateQueries({ queryKey: STATUS_KEY }),
    });
}

export default function TwoFactorPanel() {
    const { data: status, isLoading } = useTwoFactorStatus();
    const disable = useDisableTwoFactor();
    const [confirmOpen, setConfirmOpen] = useState(false);

    const handleSetup = () => {
        toast.info('Yönlendiriliyor', 'Authenticator kurulum sayfasına götürüleceksin');
        keycloak.login({
            action: 'CONFIGURE_TOTP',
            redirectUri: window.location.href,
        });
    };

    const handleDisable = async () => {
        try {
            await disable.mutateAsync();
            toast.success('2FA devre dışı', 'İki adımlı doğrulama hesabından kaldırıldı');
            setConfirmOpen(false);
        } catch (err) {
            toast.error('İşlem başarısız', err?.response?.data?.message || 'Tekrar dene');
        }
    };

    if (isLoading) {
        return (
            <div className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-xs text-fg-muted">
                <Loader2 className="h-3.5 w-3.5 animate-spin text-accent" />
                Durum kontrol ediliyor…
            </div>
        );
    }

    const enabled = !!status?.configured;

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
                    ? 'Hesabın iki adımlı doğrulama ile korunuyor. Güvenliği yeniden kurmak veya devre dışı bırakmak için aşağıdaki seçenekleri kullan.'
                    : 'Google/Microsoft Authenticator ile hesabını ek güvenlik katmanıyla koru.'}
            </p>

            {enabled ? (
                <div className="flex gap-2">
                    <button
                        onClick={handleSetup}
                        className="flex-1 flex items-center justify-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated hover:bg-surface text-fg py-2.5 text-xs font-semibold transition-colors cursor-pointer"
                    >
                        <RefreshCw className="h-3.5 w-3.5 text-accent" />
                        Yeniden kur
                    </button>
                    <button
                        onClick={() => setConfirmOpen(true)}
                        disabled={disable.isPending}
                        className="flex-1 flex items-center justify-center gap-1.5 rounded-lg border border-danger/30 bg-danger/5 hover:bg-danger/10 text-danger py-2.5 text-xs font-semibold transition-colors cursor-pointer disabled:opacity-50"
                    >
                        <ShieldOff className="h-3.5 w-3.5" />
                        Devre dışı bırak
                    </button>
                </div>
            ) : (
                <button
                    onClick={handleSetup}
                    className="w-full flex items-center justify-center gap-2 rounded-lg py-2.5 text-xs font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
                >
                    <Shield className="h-3.5 w-3.5" />
                    2FA'yı Kur
                </button>
            )}

            <p className="text-[10px] text-fg-subtle leading-relaxed px-1">
                {enabled
                    ? 'Devre dışı bırakırsan tek faktörlü oturum açma aktif olur, hesap güvenliği düşer.'
                    : 'Doğrulama kurulum sayfasına yönlendirileceksin.'}
            </p>

            <ConfirmDialog
                open={confirmOpen}
                title="2FA devre dışı bırakılsın mı?"
                message="Hesabın artık tek faktörlü oturum açma ile korunacak. Bu işlem geri alınabilir — istediğin zaman tekrar kurabilirsin."
                confirmLabel="Devre dışı bırak"
                cancelLabel="Vazgeç"
                variant="danger"
                loading={disable.isPending}
                onConfirm={handleDisable}
                onCancel={() => setConfirmOpen(false)}
            />
        </div>
    );
}
