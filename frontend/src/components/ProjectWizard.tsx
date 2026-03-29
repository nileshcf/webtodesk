import { useState } from 'react';
import { Monitor, Globe, Tag, Cpu, ChevronRight, ChevronLeft, Check } from 'lucide-react';
import { LicenseTier } from '../types/license';

// ─── Types ──────────────────────────────────────────────

interface ModuleInfo {
  key: string;
  name: string;
  description: string;
  requiredTier: LicenseTier;
  available: boolean;
}

interface WizardData {
  projectName: string;
  websiteUrl: string;
  appTitle: string;
  iconFile: string;
  enabledModules: string[];
  targetPlatform: 'auto' | 'win' | 'linux';
}

interface ProjectWizardProps {
  userTier?: LicenseTier;
  devMode?: boolean;
  initialData?: Partial<WizardData>;
  onSubmit: (data: WizardData) => void | Promise<void>;
  onCancel: () => void;
  submitLabel?: string;
}

// ─── Static module catalogue (mirrors ModuleRegistry.java) ──

const ALL_MODULES: ModuleInfo[] = [
  {
    key: 'splash-screen',
    name: 'Splash Screen',
    description: 'Branded loading screen while the main URL loads',
    requiredTier: LicenseTier.TRIAL,
    available: true,
  },
  {
    key: 'offline',
    name: 'Offline Detection',
    description: 'Shows a friendly error page when the network connection is lost',
    requiredTier: LicenseTier.TRIAL,
    available: true,
  },
  {
    key: 'badge',
    name: 'Badge Count',
    description: 'Set dock/taskbar badge counter via IPC from the renderer',
    requiredTier: LicenseTier.TRIAL,
    available: true,
  },
  {
    key: 'screen-protect',
    name: 'Screen Protection',
    description: 'OS-level content protection to prevent screenshots and recordings',
    requiredTier: LicenseTier.PRO,
    available: false,
  },
  {
    key: 'deep-link',
    name: 'Deep Link',
    description: 'Register a custom URL protocol so the app can be launched via myapp:// links',
    requiredTier: LicenseTier.PRO,
    available: false,
  },
];

const STEPS = ['Basic Info', 'Features', 'Review'] as const;

// ─── Tier badge helper ──────────────────────────────────

const tierColors: Record<string, string> = {
  TRIAL:    'bg-white/5 text-white/50 border border-white/10',
  STARTER:  'bg-accent-blue/10 text-accent-blue border border-accent-blue/20',
  PRO:      'bg-purple-500/10 text-purple-300 border border-purple-500/20',
  LIFETIME: 'bg-amber-500/10 text-amber-300 border border-amber-500/20',
};

function TierPill({ tier }: { tier: string }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${tierColors[tier] ?? 'bg-white/5 text-white/40'}`}>
      {tier}
    </span>
  );
}

// ─── Step 1 — Basic Info ────────────────────────────────

function BasicInfoStep({
  data,
  onChange,
}: {
  data: WizardData;
  onChange: (patch: Partial<WizardData>) => void;
}) {
  return (
    <div className="space-y-5">
      <div>
        <label className="block text-xs font-medium text-white/50 uppercase tracking-wider mb-2">
          <Globe size={11} className="inline mr-1.5 mb-0.5" />
          Project Name <span className="text-red-400">*</span>
        </label>
        <input
          type="text"
          value={data.projectName}
          onChange={e => onChange({ projectName: e.target.value })}
          placeholder="my-awesome-app"
          className="input-field text-sm"
        />
        <p className="mt-1.5 text-xs text-white/25">Letters, numbers, hyphens and underscores only (max 64 chars).</p>
      </div>

      <div>
        <label className="block text-xs font-medium text-white/50 uppercase tracking-wider mb-2">
          <Globe size={11} className="inline mr-1.5 mb-0.5" />
          Website URL <span className="text-red-400">*</span>
        </label>
        <input
          type="url"
          value={data.websiteUrl}
          onChange={e => onChange({ websiteUrl: e.target.value })}
          placeholder="https://yourapp.com"
          className="input-field text-sm"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-white/50 uppercase tracking-wider mb-2">
          <Tag size={11} className="inline mr-1.5 mb-0.5" />
          App Title <span className="text-red-400">*</span>
        </label>
        <input
          type="text"
          value={data.appTitle}
          onChange={e => onChange({ appTitle: e.target.value })}
          placeholder="My Awesome App"
          className="input-field text-sm"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-white/50 uppercase tracking-wider mb-2">
          <Monitor size={11} className="inline mr-1.5 mb-0.5" />
          Icon File
        </label>
        <input
          type="text"
          value={data.iconFile}
          onChange={e => onChange({ iconFile: e.target.value })}
          placeholder="icon.ico"
          className="input-field text-sm"
        />
        <p className="mt-1.5 text-xs text-white/25">Leave blank to use the default icon.ico.</p>
      </div>
    </div>
  );
}

// ─── Step 2 — Features ──────────────────────────────────

function FeaturesStep({
  data,
  onChange,
  userTier,
  devMode,
}: {
  data: WizardData;
  onChange: (patch: Partial<WizardData>) => void;
  userTier: LicenseTier;
  devMode: boolean;
}) {
  const tierRank: Record<LicenseTier, number> = {
    [LicenseTier.TRIAL]:    0,
    [LicenseTier.STARTER]:  1,
    [LicenseTier.PRO]:      2,
    [LicenseTier.LIFETIME]: 3,
  };

  const isAccessible = (requiredTier: LicenseTier) =>
    devMode || tierRank[userTier] >= tierRank[requiredTier];

  const toggleModule = (key: string, accessible: boolean) => {
    if (!accessible) return;
    const current = data.enabledModules;
    onChange({
      enabledModules: current.includes(key)
        ? current.filter(k => k !== key)
        : [...current, key],
    });
  };

  return (
    <div className="space-y-3">
      {devMode && (
        <div className="flex items-center gap-2 rounded-xl bg-amber-500/10 border border-amber-500/20 px-3 py-2.5">
          <span className="text-amber-400 text-xs font-bold">⚡ DEV MODE</span>
          <span className="text-amber-300/80 text-xs">All modules unlocked — tier limits bypassed.</span>
        </div>
      )}

      <p className="text-xs text-white/40">
        Toggle the modules to bundle in your desktop app.{!devMode && ' Locked modules require a plan upgrade.'}
      </p>

      {ALL_MODULES.map(mod => {
        const accessible = isAccessible(mod.requiredTier);
        const enabled = data.enabledModules.includes(mod.key);

        return (
          <div
            key={mod.key}
            onClick={() => toggleModule(mod.key, accessible)}
            className={`flex items-start gap-3 rounded-xl border p-3.5 transition-all ${
              !accessible
                ? 'cursor-not-allowed border-white/5 bg-white/[0.02] opacity-40'
                : enabled
                ? 'cursor-pointer border-accent-blue/40 bg-accent-blue/10'
                : 'cursor-pointer border-white/8 bg-white/[0.03] hover:border-white/15 hover:bg-white/[0.06]'
            }`}
          >
            <div className="mt-0.5 flex-shrink-0">
              <div className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                !accessible ? 'bg-white/10' : enabled ? 'bg-accent-blue' : 'bg-white/15'
              }`}>
                <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow transition-transform ${
                  enabled && accessible ? 'translate-x-4' : 'translate-x-0.5'
                }`} />
              </div>
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-sm font-semibold text-white">{mod.name}</span>
                {devMode
                  ? <span className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-bold bg-amber-500/15 text-amber-400 border border-amber-500/25">DEV</span>
                  : <TierPill tier={mod.requiredTier} />}
              </div>
              <p className="mt-0.5 text-xs text-white/40">{mod.description}</p>
              {!accessible && !devMode && (
                <p className="mt-1 text-xs font-medium text-amber-400/70">Requires {mod.requiredTier} plan</p>
              )}
            </div>
          </div>
        );
      })}

      {/* OS Target — win / linux only, no auto */}
      <div className="mt-2 pt-4 border-t border-white/[0.06]">
        <p className="text-xs font-medium text-white/50 uppercase tracking-wider mb-2.5">
          <Cpu size={11} className="inline mr-1.5 mb-0.5" />
          Build Target OS
        </p>
        <div className="grid grid-cols-2 gap-2">
          {(['win', 'linux'] as const).map(t => (
            <button
              key={t}
              type="button"
              onClick={() => onChange({ targetPlatform: t })}
              className={`rounded-xl border px-4 py-3 text-sm font-semibold transition-all ${
                data.targetPlatform === t
                  ? 'border-accent-blue/50 bg-accent-blue/15 text-white'
                  : 'border-white/8 bg-white/[0.03] text-white/50 hover:border-white/15 hover:text-white/80'
              }`}
            >
              {t === 'win' ? '🪟 Windows' : '🐧 Linux'}
              <span className="block text-xs font-normal mt-0.5 opacity-60">
                {t === 'win' ? '.exe / .msi' : '.AppImage / .deb'}
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── Step 3 — Review ────────────────────────────────────

function ReviewStep({ data }: { data: WizardData }) {
  const moduleNames = ALL_MODULES.filter(m => data.enabledModules.includes(m.key)).map(m => m.name);
  const osLabel = data.targetPlatform === 'win' ? '🪟 Windows (.exe / .msi)' : '🐧 Linux (.AppImage / .deb)';

  return (
    <div className="space-y-4">
      <p className="text-xs text-white/40">Review your configuration before creating the project.</p>

      <div className="rounded-xl border border-white/[0.07] overflow-hidden divide-y divide-white/[0.04]">
        {[
          { label: 'Project Name', value: data.projectName || '—' },
          { label: 'Website URL',  value: data.websiteUrl  || '—' },
          { label: 'App Title',    value: data.appTitle    || '—' },
          { label: 'Icon',         value: data.iconFile || 'icon.ico (default)' },
          { label: 'Build Target', value: osLabel },
        ].map(row => (
          <div key={row.label} className="flex items-baseline gap-4 px-4 py-3">
            <span className="w-28 flex-shrink-0 text-xs font-medium text-white/35 uppercase tracking-wide">{row.label}</span>
            <span className="text-sm text-white/80 break-all">{row.value}</span>
          </div>
        ))}
        <div className="flex items-baseline gap-4 px-4 py-3">
          <span className="w-28 flex-shrink-0 text-xs font-medium text-white/35 uppercase tracking-wide">Modules</span>
          {moduleNames.length === 0 ? (
            <span className="text-sm text-white/30">None selected</span>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {moduleNames.map(name => (
                <span key={name} className="inline-flex items-center rounded-full bg-accent-blue/15 border border-accent-blue/25 px-2 py-0.5 text-xs font-medium text-accent-blue">
                  {name}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Main Wizard ────────────────────────────────────────

export default function ProjectWizard({
  userTier = LicenseTier.TRIAL,
  devMode = false,
  initialData,
  onSubmit,
  onCancel,
  submitLabel = 'Create Project',
}: ProjectWizardProps) {
  const [step, setStep] = useState<number>(0);
  const [submitting, setSubmitting] = useState(false);
  const [data, setData] = useState<WizardData>({
    projectName: '',
    websiteUrl:  '',
    appTitle:    '',
    iconFile:    '',
    enabledModules: [],
    targetPlatform: 'linux',
    ...initialData,
  });

  const onChange = (patch: Partial<WizardData>) => setData(prev => ({ ...prev, ...patch }));

  const canProceed = () => {
    if (step === 0) {
      return data.projectName.trim() !== '' &&
             data.websiteUrl.trim() !== '' &&
             data.appTitle.trim() !== '';
    }
    return true;
  };

  const handleNext = () => {
    if (step < STEPS.length - 1) setStep(s => s + 1);
  };

  const handleBack = () => {
    if (step > 0) setStep(s => s - 1);
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      await onSubmit(data);
    } finally {
      setSubmitting(false);
    }
  };

  const isLastStep = step === STEPS.length - 1;

  return (
    <div className="flex flex-col h-full">
      {/* Step indicators */}
      <div className="flex items-center px-6 pt-5 pb-4">
        {STEPS.map((label, idx) => (
          <div key={label} className="flex items-center">
            <div className="flex items-center gap-2">
              <div className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold transition-all ${
                idx < step
                  ? 'bg-white text-black'
                  : idx === step
                  ? 'bg-accent-blue text-white'
                  : 'bg-white/10 text-white/30'
              }`}>
                {idx < step ? <Check size={12} strokeWidth={3} /> : idx + 1}
              </div>
              <span className={`text-xs font-medium ${
                idx === step ? 'text-white' : idx < step ? 'text-white/50' : 'text-white/25'
              }`}>{label}</span>
            </div>
            {idx < STEPS.length - 1 && (
              <div className={`mx-3 h-px w-8 transition-colors ${idx < step ? 'bg-white/30' : 'bg-white/8'}`} />
            )}
          </div>
        ))}
      </div>

      {/* Step content */}
      <div className="flex-1 overflow-y-auto px-6 pb-2">
        {step === 0 && <BasicInfoStep data={data} onChange={onChange} />}
        {step === 1 && <FeaturesStep data={data} onChange={onChange} userTier={userTier} devMode={devMode} />}
        {step === 2 && <ReviewStep data={data} />}
      </div>

      {/* Navigation */}
      <div className="flex items-center justify-between border-t border-white/[0.06] px-6 py-4">
        <button
          type="button"
          onClick={step === 0 ? onCancel : handleBack}
          className="btn-ghost !py-2 !px-4 !text-sm flex items-center gap-1.5"
        >
          {step > 0 && <ChevronLeft size={14} />}
          {step === 0 ? 'Cancel' : 'Back'}
        </button>

        {isLastStep ? (
          <button
            type="button"
            onClick={handleSubmit}
            disabled={submitting}
            className="btn-primary !py-2 !px-5 !text-sm"
          >
            {submitting ? 'Creating…' : submitLabel}
          </button>
        ) : (
          <button
            type="button"
            onClick={handleNext}
            disabled={!canProceed()}
            className="btn-primary !py-2 !px-5 !text-sm flex items-center gap-1.5"
          >
            Next <ChevronRight size={14} />
          </button>
        )}
      </div>
    </div>
  );
}
