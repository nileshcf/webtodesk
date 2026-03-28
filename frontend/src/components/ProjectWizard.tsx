import { useState } from 'react';
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
}

interface ProjectWizardProps {
  userTier?: LicenseTier;
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
type Step = typeof STEPS[number];

// ─── Tier badge helper ──────────────────────────────────

const tierColors: Record<string, string> = {
  TRIAL:    'bg-gray-100 text-gray-600',
  STARTER:  'bg-blue-100 text-blue-700',
  PRO:      'bg-purple-100 text-purple-700',
  LIFETIME: 'bg-amber-100 text-amber-700',
};

function TierPill({ tier }: { tier: string }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${tierColors[tier] ?? 'bg-gray-100 text-gray-600'}`}>
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
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Project Name <span className="text-red-500">*</span>
        </label>
        <input
          type="text"
          value={data.projectName}
          onChange={e => onChange({ projectName: e.target.value })}
          placeholder="my-awesome-app"
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <p className="mt-1 text-xs text-gray-500">
          Letters, numbers, hyphens and underscores only (max 64 chars).
        </p>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Website URL <span className="text-red-500">*</span>
        </label>
        <input
          type="url"
          value={data.websiteUrl}
          onChange={e => onChange({ websiteUrl: e.target.value })}
          placeholder="https://yourapp.com"
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          App Title <span className="text-red-500">*</span>
        </label>
        <input
          type="text"
          value={data.appTitle}
          onChange={e => onChange({ appTitle: e.target.value })}
          placeholder="My Awesome App"
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Icon File
        </label>
        <input
          type="text"
          value={data.iconFile}
          onChange={e => onChange({ iconFile: e.target.value })}
          placeholder="icon.ico"
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <p className="mt-1 text-xs text-gray-500">
          Leave blank to use the default icon.ico.
        </p>
      </div>
    </div>
  );
}

// ─── Step 2 — Features ──────────────────────────────────

function FeaturesStep({
  data,
  onChange,
  userTier,
}: {
  data: WizardData;
  onChange: (patch: Partial<WizardData>) => void;
  userTier: LicenseTier;
}) {
  const tierRank: Record<LicenseTier, number> = {
    [LicenseTier.TRIAL]:    0,
    [LicenseTier.STARTER]:  1,
    [LicenseTier.PRO]:      2,
    [LicenseTier.LIFETIME]: 3,
  };

  const isAccessible = (requiredTier: LicenseTier) =>
    tierRank[userTier] >= tierRank[requiredTier];

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
      <p className="text-sm text-gray-600">
        Select the modules to include in your desktop app. Locked modules require a plan upgrade.
      </p>
      {ALL_MODULES.map(mod => {
        const accessible = isAccessible(mod.requiredTier);
        const enabled = data.enabledModules.includes(mod.key);

        return (
          <div
            key={mod.key}
            onClick={() => toggleModule(mod.key, accessible)}
            className={`flex items-start gap-3 rounded-xl border p-4 transition-colors ${
              !accessible
                ? 'cursor-not-allowed border-gray-200 bg-gray-50 opacity-60'
                : enabled
                ? 'cursor-pointer border-indigo-400 bg-indigo-50'
                : 'cursor-pointer border-gray-200 bg-white hover:border-indigo-300 hover:bg-indigo-50/30'
            }`}
          >
            {/* Checkbox */}
            <div
              className={`mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded border-2 transition-colors ${
                !accessible
                  ? 'border-gray-300 bg-gray-100'
                  : enabled
                  ? 'border-indigo-600 bg-indigo-600'
                  : 'border-gray-300 bg-white'
              }`}
            >
              {enabled && accessible && (
                <svg className="h-3 w-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                </svg>
              )}
              {!accessible && (
                <svg className="h-3 w-3 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
              )}
            </div>

            {/* Content */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-sm font-semibold text-gray-800">{mod.name}</span>
                <TierPill tier={mod.requiredTier} />
              </div>
              <p className="mt-0.5 text-xs text-gray-500">{mod.description}</p>
              {!accessible && (
                <p className="mt-1 text-xs font-medium text-amber-600">
                  Requires {mod.requiredTier} plan
                </p>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ─── Step 3 — Review ────────────────────────────────────

function ReviewStep({ data }: { data: WizardData }) {
  const moduleNames = ALL_MODULES.filter(m => data.enabledModules.includes(m.key)).map(m => m.name);

  return (
    <div className="space-y-5">
      <p className="text-sm text-gray-600">Review your configuration before creating the project.</p>

      <div className="rounded-xl border border-gray-200 overflow-hidden divide-y divide-gray-100">
        {[
          { label: 'Project Name', value: data.projectName || '—' },
          { label: 'Website URL',  value: data.websiteUrl  || '—' },
          { label: 'App Title',    value: data.appTitle    || '—' },
          { label: 'Icon File',    value: data.iconFile || 'icon.ico (default)' },
        ].map(row => (
          <div key={row.label} className="flex items-baseline gap-4 px-4 py-3">
            <span className="w-36 flex-shrink-0 text-xs font-medium text-gray-500 uppercase tracking-wide">
              {row.label}
            </span>
            <span className="text-sm text-gray-800 break-all">{row.value}</span>
          </div>
        ))}
        <div className="flex items-baseline gap-4 px-4 py-3">
          <span className="w-36 flex-shrink-0 text-xs font-medium text-gray-500 uppercase tracking-wide">
            Modules
          </span>
          {moduleNames.length === 0 ? (
            <span className="text-sm text-gray-400">None selected</span>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {moduleNames.map(name => (
                <span
                  key={name}
                  className="inline-flex items-center rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-medium text-indigo-700"
                >
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
      <div className="flex items-center gap-0 px-6 pt-6 pb-5">
        {STEPS.map((label, idx) => (
          <div key={label} className="flex items-center">
            <div className="flex items-center gap-2">
              <div
                className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold transition-colors ${
                  idx < step
                    ? 'bg-indigo-600 text-white'
                    : idx === step
                    ? 'bg-indigo-600 text-white ring-4 ring-indigo-100'
                    : 'bg-gray-200 text-gray-500'
                }`}
              >
                {idx < step ? (
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                ) : (
                  idx + 1
                )}
              </div>
              <span
                className={`text-sm font-medium ${
                  idx === step ? 'text-indigo-700' : idx < step ? 'text-gray-700' : 'text-gray-400'
                }`}
              >
                {label}
              </span>
            </div>
            {idx < STEPS.length - 1 && (
              <div className={`mx-3 h-px w-10 ${idx < step ? 'bg-indigo-400' : 'bg-gray-200'}`} />
            )}
          </div>
        ))}
      </div>

      {/* Step content */}
      <div className="flex-1 overflow-y-auto px-6">
        {step === 0 && <BasicInfoStep data={data} onChange={onChange} />}
        {step === 1 && <FeaturesStep data={data} onChange={onChange} userTier={userTier} />}
        {step === 2 && <ReviewStep data={data} />}
      </div>

      {/* Navigation */}
      <div className="flex items-center justify-between border-t border-gray-200 px-6 py-4 mt-4">
        <button
          onClick={step === 0 ? onCancel : handleBack}
          className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
        >
          {step === 0 ? 'Cancel' : 'Back'}
        </button>

        {isLastStep ? (
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-60 transition-colors"
          >
            {submitting ? 'Creating…' : submitLabel}
          </button>
        ) : (
          <button
            onClick={handleNext}
            disabled={!canProceed()}
            className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-60 transition-colors"
          >
            Next
          </button>
        )}
      </div>
    </div>
  );
}
