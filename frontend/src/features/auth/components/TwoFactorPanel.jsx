import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Shield, ShieldOff, RefreshCw, AlertTriangle, CheckCircle } from 'lucide-react';
import keycloak from '../lib/keycloak';
import { userCredentialService } from '../../../shared/services/userCredentialService';
import { toast } from '../../../shared/components/feedback/Toast';
import ConfirmDialog from '../../../shared/components/modal/ConfirmDialog';
import Spinner from '../../../shared/components/feedback/Spinner';

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
    const { t } = useTranslation();
    const { data: status, isLoading } = useTwoFactorStatus();
    const disable = useDisableTwoFactor();
    const [confirmOpen, setConfirmOpen] = useState(false);

    const handleSetup = () => {
        toast.info(t('twoFactor.redirecting'), t('twoFactor.redirectingHint'));
        keycloak.login({
            action: 'CONFIGURE_TOTP',
            redirectUri: window.location.href,
        });
    };

    const handleDisable = async () => {
        try {
            await disable.mutateAsync();
            toast.success(t('twoFactor.disabledTitle'), t('twoFactor.disabledBody'));
            setConfirmOpen(false);
        } catch (err) {
            toast.error(t('error.actionFailed'), err?.response?.data?.message || t('common.retry'));
        }
    };

    if (isLoading) {
        return (
            <div className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-xs text-fg-muted">
                <Spinner size="sm" tone="accent" />
                {t('twoFactor.statusLoading')}
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
                <span>{enabled ? t('twoFactor.active') : t('twoFactor.inactive')}</span>
            </div>
            <p className="text-[11px] text-fg-muted leading-relaxed px-1">
                {enabled ? t('twoFactor.descActive') : t('twoFactor.descInactive')}
            </p>

            {enabled ? (
                <div className="flex gap-2">
                    <button
                        onClick={handleSetup}
                        className="flex-1 flex items-center justify-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated hover:bg-surface text-fg py-2.5 text-xs font-semibold transition-colors cursor-pointer"
                    >
                        <RefreshCw className="h-3.5 w-3.5 text-accent" />
                        {t('twoFactor.reSetup')}
                    </button>
                    <button
                        onClick={() => setConfirmOpen(true)}
                        disabled={disable.isPending}
                        className="flex-1 flex items-center justify-center gap-1.5 rounded-lg border border-danger/30 bg-danger/5 hover:bg-danger/10 text-danger py-2.5 text-xs font-semibold transition-colors cursor-pointer disabled:opacity-50"
                    >
                        <ShieldOff className="h-3.5 w-3.5" />
                        {t('twoFactor.disable')}
                    </button>
                </div>
            ) : (
                <button
                    onClick={handleSetup}
                    className="w-full flex items-center justify-center gap-2 rounded-lg py-2.5 text-xs font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
                >
                    <Shield className="h-3.5 w-3.5" />
                    {t('twoFactor.setupCta')}
                </button>
            )}

            <p className="text-[10px] text-fg-subtle leading-relaxed px-1">
                {enabled ? t('twoFactor.footerActive') : t('twoFactor.footerInactive')}
            </p>

            <ConfirmDialog
                open={confirmOpen}
                title={t('twoFactor.confirm.title')}
                message={t('twoFactor.confirm.message')}
                confirmLabel={t('twoFactor.disable')}
                cancelLabel={t('common.cancel')}
                variant="danger"
                loading={disable.isPending}
                onConfirm={handleDisable}
                onCancel={() => setConfirmOpen(false)}
            />
        </div>
    );
}
