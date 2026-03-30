import { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import {
  Bell,
  Clipboard,
  ExternalLink,
  EyeOff,
  Keyboard,
  Link2,
  Moon,
  Monitor,
  MousePointer2,
  RefreshCw,
  Shield,
  WifiOff,
} from 'lucide-react';

const MODULES = [
  { key: 'splash-screen', name: 'Splash Screen', tier: 'TRIAL' },
  { key: 'offline', name: 'Offline Detection', tier: 'TRIAL' },
  { key: 'badge', name: 'Badge Count', tier: 'TRIAL' },
  { key: 'domain-lock', name: 'Domain Lock', tier: 'TRIAL' },
  { key: 'title-bar', name: 'Title Bar', tier: 'TRIAL' },
  { key: 'watermark', name: 'Watermark Badge', tier: 'TRIAL' },
  { key: 'expiry', name: 'Trial Expiry', tier: 'TRIAL' },
  { key: 'notifications', name: 'Native Notifications', tier: 'STARTER' },
  { key: 'system-tray', name: 'System Tray', tier: 'STARTER' },
  { key: 'dark-mode', name: 'Dark / Light Sync', tier: 'STARTER' },
  { key: 'right-click', name: 'Right-Click Control', tier: 'STARTER' },
  { key: 'auto-update', name: 'Auto Update', tier: 'STARTER' },
  { key: 'key-bindings', name: 'Key Bindings', tier: 'STARTER' },
  { key: 'window-polish', name: 'Window Polish', tier: 'STARTER' },
  { key: 'clipboard', name: 'Clipboard Integration', tier: 'STARTER' },
  { key: 'screen-protect', name: 'Screen Protection', tier: 'PRO' },
  { key: 'deep-link', name: 'Deep Link', tier: 'PRO' },
] as const;

type ModuleKey = (typeof MODULES)[number]['key'];

const DEFAULT_ENABLED: ModuleKey[] = ['splash-screen', 'title-bar', 'watermark', 'window-polish'];

function Toggle({ checked, onChange }: { checked: boolean; onChange: (next: boolean) => void }) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${checked ? 'bg-[#6C63FF]' : 'bg-white/15'}`}
    >
      <span
        className={`inline-block h-3.5 w-3.5 rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-4' : 'translate-x-0.5'}`}
      />
    </button>
  );
}

export default function ModuleLabPage() {
  const [enabled, setEnabled] = useState<ModuleKey[]>(DEFAULT_ENABLED);
  const [appTitle, setAppTitle] = useState('My Awesome App');
  const [websiteUrl, setWebsiteUrl] = useState('https://example.com/dashboard');
  const [accentColor, setAccentColor] = useState('#6C63FF');
  const [watermarkText, setWatermarkText] = useState('Trial Build');
  const [offline, setOffline] = useState(false);
  const [blocked, setBlocked] = useState(false);
  const [expired, setExpired] = useState(false);
  const [showNotification, setShowNotification] = useState(false);
  const [showTrayMenu, setShowTrayMenu] = useState(false);
  const [showContextMenu, setShowContextMenu] = useState(false);
  const [darkPreview, setDarkPreview] = useState(true);
  const [clipboardValue, setClipboardValue] = useState('Sample clipboard text');
  const [badgeCount, setBadgeCount] = useState(7);
  const [progress, setProgress] = useState(28);

  const has = (key: ModuleKey) => enabled.includes(key);

  useEffect(() => {
    if (!has('splash-screen')) return;
    const timer = window.setInterval(() => {
      setProgress((current) => (current >= 100 ? 100 : current + 6));
    }, 250);
    return () => window.clearInterval(timer);
  }, [enabled]);

  useEffect(() => {
    if (!has('notifications') || !showNotification) return;
    const timer = window.setTimeout(() => setShowNotification(false), 2800);
    return () => window.clearTimeout(timer);
  }, [enabled, showNotification]);

  const previewClasses = useMemo(() => {
    const polished = has('window-polish');
    const dark = has('dark-mode') ? darkPreview : true;
    return [
      polished ? 'backdrop-blur-xl shadow-[0_20px_80px_rgba(108,99,255,0.18)]' : 'shadow-2xl',
      dark ? 'bg-slate-950 text-white' : 'bg-slate-100 text-slate-900',
      polished ? 'border border-white/10' : 'border border-white/5',
    ].join(' ');
  }, [darkPreview, enabled]);

  const toggleModule = (key: ModuleKey) => {
    setEnabled((current) => current.includes(key) ? current.filter((item) => item !== key) : [...current, key]);
  };

  return (
    <div className="min-h-screen pt-24 pb-16 px-6">
      <div className="max-w-7xl mx-auto space-y-6">
        <div className="glass-card p-6 sm:p-8">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-[#6C63FF]">Browser Module Lab</p>
              <h1 className="mt-2 text-3xl font-bold tracking-tight">Preview module UI without building Electron</h1>
              <p className="mt-2 max-w-3xl text-sm text-white/45">
                Toggle modules, tweak sample states, and inspect how the UI layer of each module may look inside the app shell.
                This is a visual simulator for frontend behavior, not a native Electron runtime.
              </p>
            </div>
            <div className="rounded-2xl border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-xs text-amber-200/80">
              Native OS capabilities like real tray icons, global shortcuts, screenshot blocking, and protocol registration are mocked visually here.
            </div>
          </div>
        </div>

        <div className="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
          <div className="space-y-6">
            <div className="glass-card p-5 space-y-4">
              <h2 className="text-sm font-semibold text-white/80">Preview Controls</h2>
              <div className="space-y-3">
                <div>
                  <label className="mb-1.5 block text-[10px] font-bold uppercase tracking-wider text-white/35">App Title</label>
                  <input value={appTitle} onChange={(e) => setAppTitle(e.target.value)} className="input-field" />
                </div>
                <div>
                  <label className="mb-1.5 block text-[10px] font-bold uppercase tracking-wider text-white/35">Website URL</label>
                  <input value={websiteUrl} onChange={(e) => setWebsiteUrl(e.target.value)} className="input-field" />
                </div>
                <div>
                  <label className="mb-1.5 block text-[10px] font-bold uppercase tracking-wider text-white/35">Accent Color</label>
                  <div className="flex items-center gap-2">
                    <input type="color" value={accentColor} onChange={(e) => setAccentColor(e.target.value)} className="h-10 w-12 rounded-lg border border-white/10 bg-transparent" />
                    <input value={accentColor} onChange={(e) => setAccentColor(e.target.value)} className="input-field" />
                  </div>
                </div>
                <div>
                  <label className="mb-1.5 block text-[10px] font-bold uppercase tracking-wider text-white/35">Watermark Text</label>
                  <input value={watermarkText} onChange={(e) => setWatermarkText(e.target.value)} className="input-field" />
                </div>
                <div>
                  <label className="mb-1.5 block text-[10px] font-bold uppercase tracking-wider text-white/35">Badge Count</label>
                  <input type="number" min={0} max={99} value={badgeCount} onChange={(e) => setBadgeCount(Number(e.target.value) || 0)} className="input-field" />
                </div>
              </div>
            </div>

            <div className="glass-card p-5 space-y-4">
              <h2 className="text-sm font-semibold text-white/80">Simulated States</h2>
              <div className="space-y-3 text-xs text-white/70">
                {[
                  ['Offline', offline, setOffline],
                  ['Blocked URL', blocked, setBlocked],
                  ['Expired Trial', expired, setExpired],
                  ['Dark Sync Enabled', darkPreview, setDarkPreview],
                  ['Tray Menu Open', showTrayMenu, setShowTrayMenu],
                  ['Context Menu Open', showContextMenu, setShowContextMenu],
                ].map(([label, value, setValue]) => (
                  <div key={label as string} className="flex items-center justify-between rounded-xl border border-white/[0.06] bg-white/[0.02] px-3 py-2.5">
                    <span>{label as string}</span>
                    <Toggle checked={value as boolean} onChange={setValue as (next: boolean) => void} />
                  </div>
                ))}
                <button
                  type="button"
                  onClick={() => setShowNotification(true)}
                  className="w-full rounded-xl border border-[#6C63FF]/25 bg-[#6C63FF]/10 px-3 py-2.5 text-left text-xs font-medium text-[#c9c6ff] transition hover:bg-[#6C63FF]/15"
                >
                  Trigger notification preview
                </button>
              </div>
            </div>

            <div className="glass-card p-5 space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-white/80">Modules</h2>
                <span className="text-[11px] text-white/35">{enabled.length} enabled</span>
              </div>
              <div className="space-y-2">
                {MODULES.map((module) => {
                  const active = has(module.key);
                  return (
                    <button
                      key={module.key}
                      type="button"
                      onClick={() => toggleModule(module.key)}
                      className={`flex w-full items-center justify-between rounded-xl border px-3 py-2.5 text-left transition ${active ? 'border-[#6C63FF]/30 bg-[#6C63FF]/10' : 'border-white/[0.06] bg-white/[0.02] hover:bg-white/[0.04]'}`}
                    >
                      <div>
                        <div className="text-sm font-medium text-white/85">{module.name}</div>
                        <div className="text-[10px] uppercase tracking-wider text-white/30">{module.tier}</div>
                      </div>
                      <Toggle checked={active} onChange={() => toggleModule(module.key)} />
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          <div className="glass-card p-4 sm:p-6">
            <div className="mb-4 flex items-center justify-between gap-4">
              <div>
                <h2 className="text-sm font-semibold text-white/80">App Canvas</h2>
                <p className="text-xs text-white/35">Interactive browser mock of the desktop wrapper UI.</p>
              </div>
              <div className="flex flex-wrap gap-2 text-[10px] uppercase tracking-wider text-white/35">
                {enabled.map((item) => (
                  <span key={item} className="rounded-full border border-white/10 bg-white/[0.03] px-2.5 py-1">{item}</span>
                ))}
              </div>
            </div>

            <div className="relative overflow-hidden rounded-[28px] border border-white/10 bg-[#020617] p-3">
              <div className={`relative mx-auto aspect-[16/10] w-full max-w-5xl overflow-hidden rounded-[22px] ${previewClasses}`} onContextMenu={(e) => e.preventDefault()}>
                {has('title-bar') && (
                  <div className="flex h-11 items-center justify-between border-b border-white/10 px-4" style={{ backgroundColor: has('dark-mode') && !darkPreview ? '#dbe4ff' : '#131a2b' }}>
                    <div>
                      <div className="text-sm font-semibold">{appTitle}</div>
                      <div className="text-[11px] text-white/35">Desktop wrapper preview</div>
                    </div>
                    <div className="flex items-center gap-2">
                      {has('badge') && badgeCount > 0 && (
                        <span className="rounded-full px-2 py-0.5 text-[10px] font-bold text-white" style={{ backgroundColor: accentColor }}>{badgeCount}</span>
                      )}
                      <span className="h-2.5 w-2.5 rounded-full bg-yellow-400/80" />
                      <span className="h-2.5 w-2.5 rounded-full bg-green-400/80" />
                      <span className="h-2.5 w-2.5 rounded-full bg-red-400/80" />
                    </div>
                  </div>
                )}

                <div className="relative h-full overflow-hidden">
                  <div className={`absolute inset-0 ${has('window-polish') ? 'bg-gradient-to-br from-white/5 via-transparent to-[#6C63FF]/10' : ''}`} />

                  <div className="absolute inset-0 overflow-auto p-6">
                    {has('offline') && offline && (
                      <div className="mb-4 flex items-center gap-2 rounded-2xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                        <WifiOff size={16} /> No internet connection. Offline fallback page is active.
                      </div>
                    )}

                    {has('auto-update') && (
                      <div className="mb-4 flex items-center justify-between rounded-2xl border border-cyan-500/20 bg-cyan-500/10 px-4 py-3 text-sm text-cyan-100">
                        <div className="flex items-center gap-2"><RefreshCw size={15} /> Update channel connected</div>
                        <span className="text-xs text-cyan-100/70">v1.4.2 available</span>
                      </div>
                    )}

                    <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
                      <div className="space-y-4">
                        <div className="rounded-[24px] border border-white/10 bg-white/[0.03] p-5">
                          <div className="flex items-start justify-between gap-4">
                            <div>
                              <p className="text-xs uppercase tracking-[0.24em] text-white/30">Embedded Website</p>
                              <h3 className="mt-2 text-2xl font-semibold tracking-tight">{appTitle}</h3>
                              <p className="mt-2 max-w-xl text-sm text-white/45">This area represents your wrapped website. Module UI layers stack around it to mimic how the final app may look.</p>
                            </div>
                            {has('deep-link') && (
                              <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 px-3 py-2 text-xs text-emerald-200">
                                <div className="flex items-center gap-1.5"><Link2 size={13} /> myapp://open/dashboard</div>
                              </div>
                            )}
                          </div>

                          <div className="mt-5 grid gap-3 sm:grid-cols-2">
                            {[
                              { label: 'Current URL', value: blocked ? 'https://blocked.example.com' : websiteUrl },
                              { label: 'Theme', value: has('dark-mode') ? (darkPreview ? 'Dark synced' : 'Light synced') : 'App default' },
                              { label: 'Clipboard', value: has('clipboard') ? clipboardValue : 'Module disabled' },
                              { label: 'Security', value: has('screen-protect') ? 'Capture blocked visually' : 'No visual protection' },
                            ].map((item) => (
                              <div key={item.label} className="rounded-2xl border border-white/8 bg-black/20 px-4 py-3">
                                <div className="text-[10px] uppercase tracking-wider text-white/30">{item.label}</div>
                                <div className="mt-1 text-sm font-medium text-white/80 break-all">{item.value}</div>
                              </div>
                            ))}
                          </div>
                        </div>

                        <div className="rounded-[24px] border border-white/10 bg-white/[0.03] p-5">
                          <div className="flex items-center justify-between gap-3">
                            <div>
                              <div className="text-sm font-semibold">Simulated page actions</div>
                              <div className="text-xs text-white/35">Use these to watch modules react visually.</div>
                            </div>
                            <div className="flex gap-2">
                              <button type="button" onClick={() => setBlocked((value) => !value)} className="rounded-xl border border-white/10 px-3 py-2 text-xs text-white/70 hover:bg-white/[0.04]">Toggle blocked URL</button>
                              <button type="button" onClick={() => setShowContextMenu((value) => !value)} className="rounded-xl border border-white/10 px-3 py-2 text-xs text-white/70 hover:bg-white/[0.04]">Open context menu</button>
                              <button type="button" onClick={() => setClipboardValue('Copied from preview at ' + new Date().toLocaleTimeString())} className="rounded-xl border border-white/10 px-3 py-2 text-xs text-white/70 hover:bg-white/[0.04]">Change clipboard</button>
                            </div>
                          </div>
                        </div>
                      </div>

                      <div className="space-y-4">
                        {has('system-tray') && (
                          <div className="rounded-[24px] border border-white/10 bg-white/[0.03] p-5">
                            <div className="mb-3 flex items-center gap-2 text-sm font-semibold"><Monitor size={15} /> Tray simulation</div>
                            <button type="button" onClick={() => setShowTrayMenu((value) => !value)} className="rounded-xl border border-white/10 px-3 py-2 text-xs text-white/70 hover:bg-white/[0.04]">Toggle tray menu</button>
                            {showTrayMenu && (
                              <div className="mt-3 rounded-2xl border border-white/10 bg-slate-900/95 p-2 text-sm">
                                {['Show', 'Hide', 'Reload', 'Quit'].map((item) => (
                                  <div key={item} className="rounded-xl px-3 py-2 text-white/75 hover:bg-white/[0.05]">{item}</div>
                                ))}
                              </div>
                            )}
                          </div>
                        )}

                        {has('key-bindings') && (
                          <div className="rounded-[24px] border border-white/10 bg-white/[0.03] p-5">
                            <div className="mb-3 flex items-center gap-2 text-sm font-semibold"><Keyboard size={15} /> Shortcut map</div>
                            <div className="space-y-2 text-xs text-white/65">
                              {[
                                ['Ctrl+R', 'Reload app'],
                                ['Alt+Left', 'Go back'],
                                ['Ctrl+Shift+I', 'Toggle devtools'],
                              ].map(([key, action]) => (
                                <div key={key} className="flex items-center justify-between rounded-xl border border-white/8 px-3 py-2">
                                  <span className="font-mono text-white/85">{key}</span>
                                  <span>{action}</span>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

                        {has('clipboard') && (
                          <div className="rounded-[24px] border border-white/10 bg-white/[0.03] p-5">
                            <div className="mb-3 flex items-center gap-2 text-sm font-semibold"><Clipboard size={15} /> Clipboard bridge</div>
                            <textarea value={clipboardValue} onChange={(e) => setClipboardValue(e.target.value)} className="min-h-28 w-full rounded-2xl border border-white/10 bg-black/20 px-3 py-2 text-sm text-white/80 outline-none" />
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {has('watermark') && (
                    <div className="absolute right-4 top-4 rounded-full border border-orange-400/20 bg-orange-500/85 px-3 py-1.5 text-xs font-semibold text-white shadow-lg">
                      {watermarkText}
                    </div>
                  )}

                  {has('screen-protect') && (
                    <div className="pointer-events-none absolute inset-0 bg-[repeating-linear-gradient(135deg,rgba(255,255,255,0.04)_0px,rgba(255,255,255,0.04)_12px,transparent_12px,transparent_24px)]">
                      <div className="absolute bottom-4 right-4 rounded-full border border-white/10 bg-black/45 px-3 py-1 text-[10px] uppercase tracking-[0.2em] text-white/60">
                        Capture protected preview
                      </div>
                    </div>
                  )}

                  {has('domain-lock') && blocked && (
                    <div className="absolute inset-0 flex items-center justify-center bg-slate-950/80 p-6">
                      <div className="max-w-md rounded-[28px] border border-red-500/20 bg-slate-900/95 p-6 text-center shadow-2xl">
                        <Shield className="mx-auto mb-3 text-red-300" size={28} />
                        <h3 className="text-xl font-semibold">Blocked destination</h3>
                        <p className="mt-2 text-sm text-white/45">Domain Lock prevents navigation to this URL. In the real app, you can block or redirect externally.</p>
                        <button type="button" className="mt-4 inline-flex items-center gap-2 rounded-xl border border-white/10 px-4 py-2 text-sm text-white/75">
                          <ExternalLink size={14} /> Open externally
                        </button>
                      </div>
                    </div>
                  )}

                  {has('expiry') && expired && (
                    <div className="absolute inset-0 z-20 flex items-center justify-center bg-black/85 p-6">
                      <div className="max-w-lg rounded-[32px] border border-amber-500/20 bg-slate-950/95 p-8 text-center">
                        <div className="text-xs font-bold uppercase tracking-[0.24em] text-amber-300/80">Trial Expired</div>
                        <h3 className="mt-3 text-3xl font-semibold">Upgrade to continue</h3>
                        <p className="mt-3 text-sm text-white/45">Your visual lock screen overlay appears here. This simulates the interruption users will see after expiry.</p>
                        <button type="button" className="mt-5 rounded-2xl px-5 py-3 text-sm font-semibold text-white" style={{ backgroundColor: accentColor }}>Upgrade now</button>
                      </div>
                    </div>
                  )}

                  {has('right-click') && showContextMenu && (
                    <div className="absolute bottom-8 left-8 z-10 w-48 rounded-2xl border border-white/10 bg-slate-900/95 p-2 shadow-2xl">
                      {['Copy', 'Paste', 'Select All'].map((item) => (
                        <div key={item} className="rounded-xl px-3 py-2 text-sm text-white/75 hover:bg-white/[0.05]">{item}</div>
                      ))}
                      <div className="mt-1 border-t border-white/10 px-3 pt-2 text-[11px] text-white/35">Right-click module mock</div>
                    </div>
                  )}

                  {has('notifications') && showNotification && (
                    <motion.div initial={{ opacity: 0, y: -12 }} animate={{ opacity: 1, y: 0 }} className="absolute right-4 top-16 z-10 w-72 rounded-2xl border border-white/10 bg-slate-900/95 p-4 shadow-2xl">
                      <div className="flex items-start gap-3">
                        <div className="rounded-xl bg-[#6C63FF]/20 p-2 text-[#c9c6ff]"><Bell size={16} /></div>
                        <div>
                          <div className="text-sm font-semibold">Build complete</div>
                          <div className="mt-1 text-xs text-white/45">This is how a notification surface can be previewed in-browser.</div>
                        </div>
                      </div>
                    </motion.div>
                  )}

                  {has('splash-screen') && progress < 100 && (
                    <div className="absolute inset-0 z-30 flex items-center justify-center bg-slate-950/96">
                      <div className="w-full max-w-md px-8 text-center">
                        <div className="mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-[28px] text-white shadow-2xl" style={{ background: `linear-gradient(135deg, ${accentColor}, #8b5cf6)` }}>
                          <Monitor size={28} />
                        </div>
                        <h3 className="text-2xl font-semibold">Launching {appTitle}</h3>
                        <p className="mt-2 text-sm text-white/40">Splash screen module visual preview</p>
                        <div className="mt-6 h-2 overflow-hidden rounded-full bg-white/10">
                          <div className="h-full rounded-full transition-all" style={{ width: `${progress}%`, backgroundColor: accentColor }} />
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>

            <div className="mt-5 grid gap-3 md:grid-cols-3">
              {[
                { icon: EyeOff, title: 'Visual only', text: 'Native shell capabilities are represented as mock overlays and panels.' },
                { icon: MousePointer2, title: 'Interactive', text: 'Toggle sample states like expiry, right-click, tray menu, and notifications.' },
                { icon: Moon, title: 'Theme-aware', text: 'Dark mode and window polish can be previewed as browser-side appearance changes.' },
              ].map(({ icon: Icon, title, text }) => (
                <div key={title} className="rounded-2xl border border-white/[0.06] bg-white/[0.02] p-4">
                  <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-2xl bg-[#6C63FF]/10 text-[#c9c6ff]"><Icon size={18} /></div>
                  <div className="text-sm font-semibold text-white/85">{title}</div>
                  <div className="mt-1 text-xs leading-relaxed text-white/40">{text}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
