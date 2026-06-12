import { useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { User, KeyRound, Mail, Shield, Check, Pencil, X } from 'lucide-react';
import SideDrawer from '../../shared/components/modal/SideDrawer';
import Spinner from '../../shared/components/feedback/Spinner';
import { userProfileService } from '../../shared/services/userProfileService';
import { userCredentialService } from '../../shared/services/userCredentialService';
import { toast } from '../../shared/components/feedback/toastBus';
import TwoFactorPanel from '../auth/components/TwoFactorPanel';
import EmailChangeSection from '../settings/EmailChangeSection';

const PROFILE_KEY = ['user', 'profile'];

function Section({ icon: Icon, title, children, accent = 'text-accent' }) {
  return (
    <div className="space-y-2.5">
      <div className="flex items-center gap-2 text-[11px] font-semibold text-fg-muted uppercase tracking-[0.14em]">
        <Icon className={`h-3.5 w-3.5 ${accent}`} />
        {title}
      </div>
      {children}
    </div>
  );
}

function IdentityHeader({ profile }) {
  const initial = (profile?.firstName || profile?.username || '?').charAt(0).toUpperCase();
  return (
    <div className="relative px-4 sm:px-5 pt-6 pb-5 border-b border-border-default/60 overflow-hidden" data-tour="profile-main">
      <div className="absolute inset-0 pointer-events-none opacity-60">
        <div className="absolute -top-12 -left-12 w-48 h-48 rounded-full bg-accent/20 blur-3xl" />
        <div className="absolute -bottom-8 -right-8 w-40 h-40 rounded-full bg-violet-500/15 blur-3xl" />
      </div>
      <div className="relative flex items-center gap-3">
        <span
          className="flex items-center justify-center w-14 h-14 rounded-2xl text-white text-xl font-bold shadow-lg shadow-accent/40"
          style={{ background: 'linear-gradient(135deg, var(--color-accent), var(--color-accent-secondary))' }}
        >
          {initial}
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-sm font-bold text-fg truncate">
            {[profile?.firstName, profile?.lastName].filter(Boolean).join(' ') || profile?.username || '—'}
          </h2>
          <p className="text-[11px] font-mono text-fg-muted truncate">@{profile?.username}</p>
          {profile?.email && (
            <p className="text-[10px] text-fg-subtle truncate mt-0.5">{profile.email}</p>
          )}
        </div>
      </div>
    </div>
  );
}

function IdentityForm({ profile, onSaved }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({
    username: profile?.username || '',
    firstName: profile?.firstName || '',
    lastName: profile?.lastName || '',
    profileKey: profile?.username || null,
  });

  if (!editing && draft.profileKey !== (profile?.username || null)) {
    setDraft({
      username: profile?.username || '',
      firstName: profile?.firstName || '',
      lastName: profile?.lastName || '',
      profileKey: profile?.username || null,
    });
  }

  const { username, firstName, lastName } = draft;
  const setUsername = (v) => setDraft((d) => ({ ...d, username: v }));
  const setFirstName = (v) => setDraft((d) => ({ ...d, firstName: v }));
  const setLastName = (v) => setDraft((d) => ({ ...d, lastName: v }));

  const update = useMutation({
    mutationFn: userProfileService.update,
    onSuccess: (data) => {
      queryClient.setQueryData(PROFILE_KEY, data);
      toast.success(t('profile.identity.savedTitle'), t('profile.identity.savedBody'));
      setEditing(false);
      onSaved?.();
    },
    onError: (err) => {
      toast.error(t('error.actionFailed'), err?.response?.data?.message || t('common.retry'));
    },
  });

  const dirty = username !== profile?.username
    || firstName !== (profile?.firstName || '')
    || lastName !== (profile?.lastName || '');

  if (!editing) {
    return (
      <div className="space-y-2">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 min-w-0">
          <ReadField label={t('profile.identity.firstName')} value={profile?.firstName} />
          <ReadField label={t('profile.identity.lastName')} value={profile?.lastName} />
        </div>
        <ReadField label={t('profile.identity.username')} value={profile?.username} mono />
        <button
          type="button"
          onClick={() => setEditing(true)}
          className="w-full flex items-center justify-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated hover:bg-surface text-fg py-2.5 min-h-[40px] text-xs font-semibold transition-colors cursor-pointer"
        >
          <Pencil className="h-3.5 w-3.5 text-accent" />
          {t('profile.identity.editAction')}
        </button>
      </div>
    );
  }

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!dirty) {
      setEditing(false);
      return;
    }
    update.mutate({
      username: username !== profile?.username ? username : undefined,
      firstName: firstName !== (profile?.firstName || '') ? firstName : undefined,
      lastName: lastName !== (profile?.lastName || '') ? lastName : undefined,
    });
  };

  return (
    <form className="space-y-2" onSubmit={handleSubmit}>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <FormField label={t('profile.identity.firstName')} value={firstName} onChange={setFirstName} maxLength={25} />
        <FormField label={t('profile.identity.lastName')} value={lastName} onChange={setLastName} maxLength={25} />
      </div>
      <FormField label={t('profile.identity.username')} value={username} onChange={setUsername} mono pattern="^[a-zA-Z0-9._-]+$" minLength={3} maxLength={25} required />
      <p className="text-[10px] text-fg-subtle leading-relaxed px-1">
        {t('profile.identity.usernameHint')}
      </p>
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={!dirty || update.isPending}
          className="flex-1 flex items-center justify-center gap-1.5 rounded-lg py-2.5 min-h-[40px] text-xs font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer disabled:opacity-50"
        >
          <Check className="h-3.5 w-3.5" />
          {update.isPending ? t('common.loading') : t('common.save')}
        </button>
        <button
          type="button"
          onClick={() => setEditing(false)}
          disabled={update.isPending}
          className="flex-1 flex items-center justify-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated hover:bg-surface text-fg-muted py-2.5 min-h-[40px] text-xs font-semibold transition-colors cursor-pointer disabled:opacity-50"
        >
          <X className="h-3.5 w-3.5" />
          {t('common.cancel')}
        </button>
      </div>
    </form>
  );
}

function ReadField({ label, value, mono }) {
  return (
    <div className="min-w-0 rounded-lg border border-border-default bg-bg-elevated/50 px-3 py-2 overflow-hidden">
      <p className="text-[9px] font-mono uppercase tracking-[0.14em] text-fg-subtle truncate">{label}</p>
      <p className={`mt-0.5 text-sm text-fg truncate ${mono ? 'font-mono' : 'font-medium'}`}>
        {value || '—'}
      </p>
    </div>
  );
}

function FormField({ label, value, onChange, mono, ...inputProps }) {
  return (
    <label className="block min-w-0">
      <span className="text-[9px] font-mono uppercase tracking-[0.14em] text-fg-subtle">{label}</span>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={`mt-1 w-full min-w-0 rounded-lg border border-border-default bg-bg-elevated px-3 py-2 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-2 focus:ring-accent/20 ${mono ? 'font-mono' : ''}`}
        {...inputProps}
      />
    </label>
  );
}

export default function ProfileDrawer({ isOpen, onClose }) {
  const { t } = useTranslation();
  const { data: profile, isLoading } = useQuery({
    queryKey: PROFILE_KEY,
    queryFn: userProfileService.get,
    enabled: isOpen,
    staleTime: 60_000,
  });
  const [passwordSending, setPasswordSending] = useState(false);

  const handleChangePassword = async () => {
    setPasswordSending(true);
    try {
      await userCredentialService.initiatePasswordChange(`${window.location.origin}/`);
      toast.success(t('settings.password.success'), t('settings.password.successDesc'));
      onClose();
    } catch (err) {
      toast.error(t('settings.password.error'), err?.response?.data?.message || t('settings.password.errorDesc'));
    } finally {
      setPasswordSending(false);
    }
  };

  return createPortal(
    <SideDrawer
      open={isOpen}
      onClose={onClose}
      width="min(25rem, 100vw)"
      icon={User}
      iconTint="text-accent"
      title={t('profile.title')}
      subtitle={t('profile.subtitle')}
      closeAttr="profile"
    >
      <IdentityHeader profile={profile} />
      <div className="px-4 sm:px-5 py-5 space-y-6">
        <Section icon={User} title={t('profile.identity.title')}>
          {isLoading ? (
            <div className="flex items-center gap-2 text-xs text-fg-muted">
              <Spinner size="sm" tone="accent" />
              {t('common.loading')}
            </div>
          ) : (
            <IdentityForm profile={profile} />
          )}
        </Section>

        <Section icon={Shield} title={t('settings.twoFactor')}>
          <TwoFactorPanel />
        </Section>

        <Section icon={KeyRound} title={t('settings.password.title')}>
          <button
            onClick={handleChangePassword}
            disabled={passwordSending}
            className="w-full flex items-center justify-between gap-2 rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 min-h-[40px] text-xs font-medium text-fg hover:bg-surface transition-colors cursor-pointer disabled:opacity-50"
          >
            <span className="flex items-center gap-2 min-w-0">
              <KeyRound className="h-3.5 w-3.5 text-accent shrink-0" />
              <span className="truncate">{t('settings.password.change')}</span>
            </span>
            <span className="text-[10px] text-fg-muted shrink-0 hidden sm:inline">
              {passwordSending ? t('settings.password.sending') : t('settings.password.emailLink')}
            </span>
          </button>
          <p className="text-[10px] text-fg-subtle leading-relaxed px-1 mt-1.5">
            {t('settings.password.hint')}
          </p>
        </Section>

        <Section icon={Mail} title={t('settings.email')}>
          <EmailChangeSection />
        </Section>
      </div>
    </SideDrawer>,
    document.body,
  );
}
