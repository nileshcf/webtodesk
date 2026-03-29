import { useState, KeyboardEvent } from 'react';
import { Monitor, Globe, Tag, Cpu, ChevronRight, ChevronLeft, Check, X } from 'lucide-react';
import { LicenseTier } from '../types/license';
import type {
  ModuleConfig,
  DomainLockConfig,
  TitleBarConfig,
  WatermarkConfig,
  ExpiryConfig,
  SystemTrayConfig,
  RightClickConfig,
  AutoUpdateConfig,
  KeyBindingsConfig,
  WindowPolishConfig,
  ClipboardConfig,
} from '../types';

// ─── Types ──────────────────────────────────────────────

interface ModuleInfo {
  key: string;
  name: string;
  description: string;
  requiredTier: LicenseTier;
  available: boolean;
  hasConfig: boolean;
}

export interface WizardData {
  projectName: string;
  websiteUrl: string;
  appTitle: string;
  iconFile: string;
  enabledModules: string[];
  targetPlatform: 'auto' | 'win' | 'linux';
  moduleConfig: ModuleConfig;
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
  { key: 'splash-screen', name: 'Splash Screen',     description: 'Branded loading screen while the main URL loads',                                    requiredTier: LicenseTier.TRIAL, available: true,  hasConfig: false },
  { key: 'offline',       name: 'Offline Detection', description: 'Shows a friendly error page when the network connection is lost',                    requiredTier: LicenseTier.TRIAL, available: true,  hasConfig: false },
  { key: 'badge',         name: 'Badge Count',       description: 'Set dock/taskbar badge counter via IPC from the renderer',                          requiredTier: LicenseTier.TRIAL, available: true,  hasConfig: false },
  { key: 'domain-lock',   name: 'Domain Lock',       description: 'Restrict navigation to allowed domains and block specified destinations',             requiredTier: LicenseTier.TRIAL, available: true,  hasConfig: true  },
  { key: 'title-bar',     name: 'Title Bar',         description: 'Set a custom window title that persists across page navigations',                    requiredTier: LicenseTier.TRIAL, available: true,  hasConfig: true  },
  { key: 'watermark',     name: 'Watermark Badge',   description: 'Persistent badge near window controls showing trial status and days remaining',       requiredTier: LicenseTier.TRIAL, available: true,  hasConfig: true  },
  { key: 'expiry',        name: 'Trial Expiry',      description: 'Locks the app with a full-screen overlay after a specified expiry date',             requiredTier: LicenseTier.TRIAL, available: true,  hasConfig: true  },
  { key: 'notifications',  name: 'Native Notifications',  description: 'Grants the Notification permission so the web Notifications API works natively', requiredTier: LicenseTier.STARTER, available: false, hasConfig: false },
  { key: 'system-tray',    name: 'System Tray',            description: 'Adds a tray icon with tooltip and configurable context menu',                   requiredTier: LicenseTier.STARTER, available: false, hasConfig: true  },
  { key: 'dark-mode',      name: 'Dark / Light Mode Sync', description: 'Syncs OS dark/light theme to web content via IPC and CSS class injection',      requiredTier: LicenseTier.STARTER, available: false, hasConfig: false },
  { key: 'right-click',    name: 'Right-Click Control',    description: 'Suppresses or replaces the browser context menu with a minimal native menu',    requiredTier: LicenseTier.STARTER, available: false, hasConfig: true  },
  { key: 'auto-update',    name: 'Auto-Update',            description: 'Configures electron-updater to check for and install new versions silently',    requiredTier: LicenseTier.STARTER, available: false, hasConfig: true  },
  { key: 'key-bindings',   name: 'Custom Key Bindings',    description: 'Registers configurable in-app keyboard shortcuts (reload, back, fullscreen…)',  requiredTier: LicenseTier.STARTER, available: false, hasConfig: true  },
  { key: 'window-polish',  name: 'Window Polish',          description: 'Applies visual enhancements: acrylic/vibrancy blur, always-on-top, opacity',    requiredTier: LicenseTier.STARTER, available: false, hasConfig: true  },
  { key: 'clipboard',      name: 'Clipboard Integration',  description: 'Exposes clipboard read/write to the renderer via a secure contextBridge API',    requiredTier: LicenseTier.STARTER, available: false, hasConfig: true  },
  { key: 'screen-protect', name: 'Screen Protection',      description: 'OS-level content protection to prevent screenshots and recordings',             requiredTier: LicenseTier.PRO,     available: false, hasConfig: false },
  { key: 'deep-link',      name: 'Deep Link',              description: 'Register a custom URL protocol so the app can be launched via myapp:// links',  requiredTier: LicenseTier.PRO,     available: false, hasConfig: false },
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

// ─── Toggle ─────────────────────────────────────────────

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={`flex-shrink-0 relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
        checked ? 'bg-accent-blue' : 'bg-white/15'
      }`}
    >
      <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow transition-transform ${
        checked ? 'translate-x-4' : 'translate-x-0.5'
      }`} />
    </button>
  );
}

// ─── Tag Input ──────────────────────────────────────────

function TagInput({
  values,
  onChange,
  placeholder,
}: {
  values: string[];
  onChange: (vals: string[]) => void;
  placeholder?: string;
}) {
  const [input, setInput] = useState('');

  const add = () => {
    const v = input.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/$/, '');
    if (v && !values.includes(v)) onChange([...values, v]);
    setInput('');
  };

  const handleKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); add(); }
    if (e.key === 'Backspace' && input === '' && values.length > 0) onChange(values.slice(0, -1));
  };

  return (
    <div className="flex flex-wrap gap-1.5 rounded-lg border border-white/10 bg-white/[0.04] px-2 py-1.5 min-h-[38px]">
      {values.map(v => (
        <span key={v} className="inline-flex items-center gap-1 rounded-md bg-accent-blue/20 border border-accent-blue/20 px-2 py-0.5 text-[11px] font-medium text-accent-blue">
          {v}
          <button type="button" onClick={() => onChange(values.filter(x => x !== v))} className="text-accent-blue/50 hover:text-accent-blue ml-0.5">
            <X size={9} />
          </button>
        </span>
      ))}
      <input
        type="text"
        value={input}
        onChange={e => setInput(e.target.value)}
        onKeyDown={handleKey}
        onBlur={add}
        placeholder={values.length === 0 ? placeholder : '+ add'}
        className="flex-1 min-w-[120px] bg-transparent text-xs text-white/80 placeholder:text-white/25 outline-none"
      />
    </div>
  );
}

// ─── Module Config Panels ────────────────────────────────

const cfgInput = 'w-full rounded-lg border border-white/10 bg-white/[0.04] px-2.5 py-1.5 text-xs text-white/80 placeholder:text-white/25 outline-none focus:border-accent-blue/40 transition-colors';
const cfgLabel = 'block text-[11px] font-medium text-white/40 uppercase tracking-wider mb-1';

function DomainLockPanel({ cfg, onChange }: { cfg: DomainLockConfig; onChange: (p: Partial<DomainLockConfig>) => void }) {
  return (
    <div className="space-y-3">
      <div>
        <p className={cfgLabel}>Allowed Domains</p>
        <TagInput values={cfg.allowedDomains ?? []} onChange={v => onChange({ allowedDomains: v })} placeholder="e.g. example.com — press Enter" />
        <p className="mt-1 text-[11px] text-white/25">Empty = allow all (except blocked). App base domain always allowed.</p>
      </div>
      <div>
        <p className={cfgLabel}>Blocked Domains</p>
        <TagInput values={cfg.blockedDomains ?? []} onChange={v => onChange({ blockedDomains: v })} placeholder="e.g. ads.example.com — press Enter" />
      </div>
      <div>
        <p className={cfgLabel}>Block Message</p>
        <input type="text" value={cfg.blockMessage ?? ''} onChange={e => onChange({ blockMessage: e.target.value })} placeholder="Navigation to this destination is not allowed." className={cfgInput} />
      </div>
      <label className="flex items-start gap-2.5 cursor-pointer">
        <Toggle checked={cfg.allowExternalInBrowser ?? false} onChange={v => onChange({ allowExternalInBrowser: v })} />
        <div>
          <span className="text-xs font-medium text-white/70">Open blocked URLs in system browser</span>
          <p className="text-[11px] text-white/30 mt-0.5">Opens the URL externally instead of showing a block alert.</p>
        </div>
      </label>
    </div>
  );
}

function TitleBarPanel({ cfg, onChange }: { cfg: TitleBarConfig; onChange: (p: Partial<TitleBarConfig>) => void }) {
  return (
    <div>
      <p className={cfgLabel}>Window Title</p>
      <input type="text" value={cfg.text ?? ''} onChange={e => onChange({ text: e.target.value })} placeholder="Leave blank to use App Title" className={cfgInput} />
      <p className="mt-1 text-[11px] text-white/25">Overrides the native window title and persists across page navigations.</p>
    </div>
  );
}

const WM_POSITIONS = ['top-right', 'top-left', 'bottom-right', 'bottom-left'] as const;

function WatermarkPanel({ cfg, onChange }: { cfg: WatermarkConfig; onChange: (p: Partial<WatermarkConfig>) => void }) {
  const pos = cfg.position ?? 'top-right';
  return (
    <div className="space-y-3">
      <div>
        <p className={cfgLabel}>Badge Text</p>
        <input type="text" value={cfg.text ?? ''} onChange={e => onChange({ text: e.target.value })} placeholder='Leave blank → "Powered by WebToDesk"' className={cfgInput} />
      </div>
      <div>
        <p className={cfgLabel}>Position</p>
        <div className="grid grid-cols-2 gap-1.5">
          {WM_POSITIONS.map(p => (
            <button key={p} type="button" onClick={() => onChange({ position: p })} className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition-all ${
              pos === p ? 'border-accent-blue/50 bg-accent-blue/15 text-white' : 'border-white/8 bg-white/[0.02] text-white/40 hover:text-white/70'
            }`}>{p}</button>
          ))}
        </div>
      </div>
      <label className="flex items-center gap-2.5 cursor-pointer">
        <Toggle checked={cfg.showDaysRemaining !== false} onChange={v => onChange({ showDaysRemaining: v })} />
        <span className="text-xs font-medium text-white/70">Show days remaining <span className="text-white/30">(requires Trial Expiry module)</span></span>
      </label>
    </div>
  );
}

function ExpiryPanel({ cfg, onChange }: { cfg: ExpiryConfig; onChange: (p: Partial<ExpiryConfig>) => void }) {
  const localDt = cfg.expiresAt ? new Date(cfg.expiresAt).toISOString().substring(0, 16) : '';
  return (
    <div className="space-y-3">
      <div>
        <p className={cfgLabel}>Expiry Date & Time</p>
        <input type="datetime-local" value={localDt} onChange={e => onChange({ expiresAt: e.target.value ? new Date(e.target.value).toISOString() : undefined })} className={cfgInput} />
        <p className="mt-1 text-[11px] text-white/25">App locks with a full-screen overlay at this time. Leave blank to disable.</p>
      </div>
      <div>
        <p className={cfgLabel}>Lock Message</p>
        <input type="text" value={cfg.lockMessage ?? ''} onChange={e => onChange({ lockMessage: e.target.value })} placeholder="Your trial has expired. Please upgrade to continue." className={cfgInput} />
      </div>
      <div>
        <p className={cfgLabel}>Upgrade URL</p>
        <input type="url" value={cfg.upgradeUrl ?? ''} onChange={e => onChange({ upgradeUrl: e.target.value })} placeholder="https://webtodesk.com/pricing" className={cfgInput} />
      </div>
    </div>
  );
}

function SystemTrayPanel({ cfg, onChange }: { cfg: SystemTrayConfig; onChange: (p: Partial<SystemTrayConfig>) => void }) {
  return (
    <div className="space-y-3">
      <div>
        <p className={cfgLabel}>Tray Tooltip</p>
        <input type="text" value={cfg.tooltip ?? ''} onChange={e => onChange({ tooltip: e.target.value })} placeholder="Leave blank to use App Title" className={cfgInput} />
      </div>
      <p className="text-[11px] text-white/25">Show / Hide and Quit items are always included. Additional items can be added via code.</p>
    </div>
  );
}

function RightClickPanel({ cfg, onChange }: { cfg: RightClickConfig; onChange: (p: Partial<RightClickConfig>) => void }) {
  return (
    <label className="flex items-start gap-2.5 cursor-pointer">
      <Toggle checked={cfg.disable !== false} onChange={v => onChange({ disable: v })} />
      <div>
        <span className="text-xs font-medium text-white/70">Disable right-click menu</span>
        <p className="text-[11px] text-white/30 mt-0.5">Off = replace with a minimal Copy / Paste / Select All menu.</p>
      </div>
    </label>
  );
}

function AutoUpdatePanel({ cfg, onChange }: { cfg: AutoUpdateConfig; onChange: (p: Partial<AutoUpdateConfig>) => void }) {
  return (
    <div>
      <p className={cfgLabel}>Feed URL</p>
      <input type="url" value={cfg.feedUrl ?? ''} onChange={e => onChange({ feedUrl: e.target.value })} placeholder="https://github.com/your/repo/releases/latest/download" className={cfgInput} />
      <p className="mt-1 text-[11px] text-white/25">electron-updater generic feed URL. Leave blank to disable auto-updates.</p>
    </div>
  );
}

const KEY_ACTIONS = ['reload', 'goBack', 'goForward', 'toggleDevTools', 'toggleFullscreen', 'minimize', 'maximize'];

function KeyBindingsPanel({ cfg, onChange }: { cfg: KeyBindingsConfig; onChange: (p: Partial<KeyBindingsConfig>) => void }) {
  const bindings = cfg.bindings ?? [];
  const update = (idx: number, patch: { accelerator?: string; action?: string }) => {
    const next = bindings.map((b, i) => i === idx ? { ...b, ...patch } : b);
    onChange({ bindings: next });
  };
  const add = () => onChange({ bindings: [...bindings, { accelerator: '', action: 'reload' }] });
  const remove = (idx: number) => onChange({ bindings: bindings.filter((_, i) => i !== idx) });

  return (
    <div className="space-y-2">
      {bindings.map((b, i) => (
        <div key={i} className="flex items-center gap-2">
          <input
            type="text"
            value={b.accelerator ?? ''}
            onChange={e => update(i, { accelerator: e.target.value })}
            placeholder="Ctrl+R"
            className={`${cfgInput} flex-1`}
          />
          <select
            value={b.action ?? 'reload'}
            onChange={e => update(i, { action: e.target.value })}
            className={`${cfgInput} w-40`}
          >
            {KEY_ACTIONS.map(a => <option key={a} value={a}>{a}</option>)}
          </select>
          <button type="button" onClick={() => remove(i)} className="text-white/30 hover:text-red-400 transition-colors"><X size={13} /></button>
        </div>
      ))}
      <button type="button" onClick={add} className="text-xs text-accent-blue/70 hover:text-accent-blue transition-colors mt-1">+ Add binding</button>
    </div>
  );
}

function WindowPolishPanel({ cfg, onChange }: { cfg: WindowPolishConfig; onChange: (p: Partial<WindowPolishConfig>) => void }) {
  return (
    <div className="space-y-3">
      <label className="flex items-center gap-2.5 cursor-pointer">
        <Toggle checked={cfg.alwaysOnTop === true} onChange={v => onChange({ alwaysOnTop: v })} />
        <span className="text-xs font-medium text-white/70">Always on top <span className="text-white/30">(floating above all windows)</span></span>
      </label>
      <label className="flex items-center gap-2.5 cursor-pointer">
        <Toggle checked={cfg.blur === true} onChange={v => onChange({ blur: v })} />
        <span className="text-xs font-medium text-white/70">Acrylic / vibrancy blur <span className="text-white/30">(Windows 11 + macOS)</span></span>
      </label>
      <div>
        <p className={cfgLabel}>Opacity <span className="normal-case font-normal text-white/30">({Math.round((cfg.opacity ?? 1.0) * 100)}%)</span></p>
        <input
          type="range" min="0.3" max="1.0" step="0.05"
          value={cfg.opacity ?? 1.0}
          onChange={e => onChange({ opacity: parseFloat(e.target.value) })}
          className="w-full accent-accent-blue"
        />
      </div>
    </div>
  );
}

function ClipboardPanel({ cfg, onChange }: { cfg: ClipboardConfig; onChange: (p: Partial<ClipboardConfig>) => void }) {
  return (
    <div className="space-y-3">
      <label className="flex items-center gap-2.5 cursor-pointer">
        <Toggle checked={cfg.allowRead !== false} onChange={v => onChange({ allowRead: v })} />
        <span className="text-xs font-medium text-white/70">Allow clipboard read <span className="text-white/30">(wtdClipboard.read())</span></span>
      </label>
      <label className="flex items-center gap-2.5 cursor-pointer">
        <Toggle checked={cfg.allowWrite !== false} onChange={v => onChange({ allowWrite: v })} />
        <span className="text-xs font-medium text-white/70">Allow clipboard write <span className="text-white/30">(wtdClipboard.write(text))</span></span>
      </label>
    </div>
  );
}

function ModuleConfigPanel({
  moduleKey, config, onChange,
}: {
  moduleKey: string;
  config: ModuleConfig;
  onChange: (mc: ModuleConfig) => void;
}) {
  function patch<T>(key: keyof ModuleConfig, p: Partial<T>) {
    onChange({ ...config, [key]: { ...(config[key] as T), ...p } });
  }
  if (moduleKey === 'domain-lock')   return <DomainLockPanel   cfg={config.domainLock   ?? {}} onChange={p => patch<DomainLockConfig>  ('domainLock',   p)} />;
  if (moduleKey === 'title-bar')     return <TitleBarPanel     cfg={config.titleBar     ?? {}} onChange={p => patch<TitleBarConfig>    ('titleBar',     p)} />;
  if (moduleKey === 'watermark')     return <WatermarkPanel    cfg={config.watermark    ?? {}} onChange={p => patch<WatermarkConfig>   ('watermark',    p)} />;
  if (moduleKey === 'expiry')        return <ExpiryPanel       cfg={config.expiry       ?? {}} onChange={p => patch<ExpiryConfig>      ('expiry',       p)} />;
  if (moduleKey === 'system-tray')   return <SystemTrayPanel   cfg={config.systemTray   ?? {}} onChange={p => patch<SystemTrayConfig>  ('systemTray',   p)} />;
  if (moduleKey === 'right-click')   return <RightClickPanel   cfg={config.rightClick   ?? {}} onChange={p => patch<RightClickConfig>  ('rightClick',   p)} />;
  if (moduleKey === 'auto-update')   return <AutoUpdatePanel   cfg={config.autoUpdate   ?? {}} onChange={p => patch<AutoUpdateConfig>  ('autoUpdate',   p)} />;
  if (moduleKey === 'key-bindings')  return <KeyBindingsPanel  cfg={config.keyBindings  ?? {}} onChange={p => patch<KeyBindingsConfig> ('keyBindings',  p)} />;
  if (moduleKey === 'window-polish') return <WindowPolishPanel cfg={config.windowPolish ?? {}} onChange={p => patch<WindowPolishConfig>('windowPolish',  p)} />;
  if (moduleKey === 'clipboard')     return <ClipboardPanel    cfg={config.clipboard    ?? {}} onChange={p => patch<ClipboardConfig>   ('clipboard',    p)} />;
  return null;
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
    <div className="space-y-2.5">
      {devMode && (
        <div className="flex items-center gap-2 rounded-xl bg-amber-500/10 border border-amber-500/20 px-3 py-2.5">
          <span className="text-amber-400 text-xs font-bold">⚡ DEV MODE</span>
          <span className="text-amber-300/80 text-xs">All modules unlocked — tier limits bypassed.</span>
        </div>
      )}

      <p className="text-xs text-white/40 pb-0.5">
        Toggle modules to bundle in your app.{!devMode && ' Locked modules require a plan upgrade.'}
      </p>

      {ALL_MODULES.map(mod => {
        const accessible = isAccessible(mod.requiredTier);
        const enabled = data.enabledModules.includes(mod.key);

        return (
          <div
            key={mod.key}
            className={`rounded-xl border transition-all overflow-hidden ${
              !accessible
                ? 'border-white/5 bg-white/[0.02] opacity-40'
                : enabled
                ? 'border-accent-blue/40 bg-accent-blue/10'
                : 'border-white/8 bg-white/[0.03] hover:border-white/12'
            }`}
          >
            {/* Clickable toggle row */}
            <div
              onClick={() => toggleModule(mod.key, accessible)}
              className={`flex items-start gap-3 p-3.5 ${
                accessible ? 'cursor-pointer' : 'cursor-not-allowed'
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

            {/* Per-module config panel — shown when enabled */}
            {mod.hasConfig && enabled && accessible && (
              <div
                className="px-3.5 pb-3.5 border-t border-white/[0.08]"
                onClick={e => e.stopPropagation()}
              >
                <p className="text-[11px] font-semibold text-accent-blue/70 uppercase tracking-wider pt-2.5 pb-2">Configure</p>
                <ModuleConfigPanel
                  moduleKey={mod.key}
                  config={data.moduleConfig}
                  onChange={mc => onChange({ moduleConfig: mc })}
                />
              </div>
            )}
          </div>
        );
      })}

      {/* OS Target */}
      <div className="pt-3 border-t border-white/[0.06]">
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

function moduleConfigSummary(key: string, mc: ModuleConfig): string | null {
  if (key === 'domain-lock') {
    const dl = mc.domainLock ?? {};
    const allow = dl.allowedDomains?.length ? dl.allowedDomains.join(', ') : 'all';
    const block = dl.blockedDomains?.length ? dl.blockedDomains.join(', ') : 'none';
    return `allow: ${allow} · block: ${block}`;
  }
  if (key === 'title-bar') {
    return mc.titleBar?.text ? `"${mc.titleBar.text}"` : 'uses App Title';
  }
  if (key === 'watermark') {
    const wm = mc.watermark ?? {};
    const text = wm.text || 'Powered by WebToDesk';
    const pos  = wm.position || 'top-right';
    const days = wm.showDaysRemaining !== false ? ' · days remaining' : '';
    return `${text} · ${pos}${days}`;
  }
  if (key === 'expiry') {
    const ex = mc.expiry ?? {};
    return ex.expiresAt
      ? `expires ${new Date(ex.expiresAt).toLocaleDateString(undefined, { dateStyle: 'medium' })}`
      : 'no expiry set';
  }
  if (key === 'system-tray')   return mc.systemTray?.tooltip ? `tooltip: "${mc.systemTray.tooltip}"` : 'default tooltip';
  if (key === 'right-click')   return mc.rightClick?.disable !== false ? 'disabled' : 'minimal native menu';
  if (key === 'auto-update')   return mc.autoUpdate?.feedUrl ? mc.autoUpdate.feedUrl : 'no feed URL';
  if (key === 'key-bindings') {
    const n = mc.keyBindings?.bindings?.length ?? 0;
    return n > 0 ? `${n} binding${n !== 1 ? 's' : ''} configured` : 'no bindings';
  }
  if (key === 'window-polish') {
    const wp = mc.windowPolish ?? {};
    const parts: string[] = [];
    if (wp.alwaysOnTop) parts.push('always-on-top');
    if (wp.blur) parts.push('blur');
    if (wp.opacity !== undefined && wp.opacity < 1) parts.push(`${Math.round(wp.opacity * 100)}% opacity`);
    return parts.length > 0 ? parts.join(' · ') : 'no effects';
  }
  if (key === 'clipboard') {
    const cp = mc.clipboard ?? {};
    const perms: string[] = [];
    if (cp.allowRead !== false)  perms.push('read');
    if (cp.allowWrite !== false) perms.push('write');
    return perms.length > 0 ? perms.join(' + ') : 'none';
  }
  return null;
}

function ReviewStep({ data }: { data: WizardData }) {
  const moduleNames = ALL_MODULES.filter(m => data.enabledModules.includes(m.key)).map(m => m.name);
  const configuredMods = ALL_MODULES.filter(m => m.hasConfig && data.enabledModules.includes(m.key));
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
        {configuredMods.length > 0 && (
          <div className="flex items-start gap-4 px-4 py-3">
            <span className="w-28 flex-shrink-0 text-xs font-medium text-white/35 uppercase tracking-wide pt-0.5">Config</span>
            <div className="space-y-1.5 flex-1">
              {configuredMods.map(mod => {
                const summary = moduleConfigSummary(mod.key, data.moduleConfig);
                return (
                  <div key={mod.key} className="flex items-baseline gap-2">
                    <span className="text-[11px] font-semibold text-accent-blue/80 w-20 flex-shrink-0">{mod.name}</span>
                    <span className="text-xs text-white/50 break-all">{summary ?? '—'}</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
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
    moduleConfig: {},
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
