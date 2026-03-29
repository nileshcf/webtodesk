import { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Plus, Globe, Monitor, Trash2, Download, Loader2,
  ExternalLink, X, AlertCircle, CheckCircle2, Sparkles, Rocket, Shield,
  Package, FileDown, History, Zap, Lock, TriangleAlert
} from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { conversionApi } from '../services/api';
import type { ConversionProject, ConversionStats, ElectronConfig, LicenseTier } from '../types';
import ProjectWizard, { type WizardData } from '../components/ProjectWizard';
import BuildDashboard from '../components/BuildDashboard';

// ─── Expiry helpers ──────────────────────────────────────────────────────────

function computeExpiry(licenseExpiresAt: string | null): {
  isExpired: boolean;
  daysLeft: number | null;
  level: 'ok' | 'info' | 'warning' | 'critical' | 'expired';
} {
  if (!licenseExpiresAt) return { isExpired: false, daysLeft: null, level: 'ok' };
  const msLeft = new Date(licenseExpiresAt).getTime() - Date.now();
  const daysLeft = Math.ceil(msLeft / 86_400_000);
  if (daysLeft <= 0) return { isExpired: true, daysLeft: 0, level: 'expired' };
  if (daysLeft <= 3)  return { isExpired: false, daysLeft, level: 'critical' };
  if (daysLeft <= 7)  return { isExpired: false, daysLeft, level: 'warning' };
  if (daysLeft <= 14) return { isExpired: false, daysLeft, level: 'info' };
  return { isExpired: false, daysLeft, level: 'ok' };
}

const EXPIRY_STYLE = {
  ok:       { bar: '', text: '', icon: null },
  info:     { bar: 'bg-accent-blue/10 border-accent-blue/20',   text: 'text-accent-blue',   icon: TriangleAlert },
  warning:  { bar: 'bg-accent-orange/10 border-accent-orange/20', text: 'text-accent-orange', icon: TriangleAlert },
  critical: { bar: 'bg-red-500/10 border-red-500/20',           text: 'text-red-400',       icon: AlertCircle },
  expired:  { bar: 'bg-red-500/15 border-red-500/30',           text: 'text-red-400',       icon: Lock },
} as const;

function LicenseExpiredBanner({ expiresAt }: { expiresAt: string }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      className="flex items-start gap-3 p-4 rounded-2xl mb-6 bg-red-500/10 border border-red-500/25 ring-1 ring-red-500/10"
    >
      <Lock size={16} className="text-red-400 flex-shrink-0 mt-0.5" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-red-300">License expired</p>
        <p className="text-xs text-red-400/70 mt-0.5">
          Your license expired on {new Date(expiresAt).toLocaleDateString()}. New builds and project creation are blocked.
          {' '}<span className="text-red-300 underline cursor-pointer">Upgrade to continue →</span>
        </p>
      </div>
    </motion.div>
  );
}

const TIER_META: Record<LicenseTier, { label: string; color: string; bg: string; ring: string }> = {
  TRIAL:    { label: 'Trial',    color: 'text-white/50',       bg: 'bg-white/[0.05]',       ring: 'ring-white/10' },
  STARTER:  { label: 'Starter',  color: 'text-accent-blue',    bg: 'bg-accent-blue/10',     ring: 'ring-accent-blue/20' },
  PRO:      { label: 'Pro',      color: 'text-accent-violet',  bg: 'bg-accent-violet/10',   ring: 'ring-accent-violet/20' },
  LIFETIME: { label: 'Lifetime', color: 'text-accent-green',   bg: 'bg-accent-green/10',    ring: 'ring-accent-green/20' },
};

function TierQuotaBanner({ stats }: { stats: ConversionStats }) {
  const meta = TIER_META[stats.tier] ?? TIER_META.TRIAL;
  const used = stats.buildsAllowed - stats.buildsRemaining;
  const pct = stats.buildsAllowed > 0 ? Math.min(100, (used / stats.buildsAllowed) * 100) : 0;
  const quotaColor = pct >= 90 ? 'bg-red-500' : pct >= 70 ? 'bg-accent-orange' : 'bg-accent-blue';
  const expiry = computeExpiry(stats.licenseExpiresAt);
  const expiryStyle = EXPIRY_STYLE[expiry.level];
  const ExpiryIcon = expiryStyle.icon;

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.03 }}
      className="glass-card p-4 sm:p-5 mb-6"
    >
      <div className="flex flex-col sm:flex-row sm:items-center gap-4">
        {/* Tier badge */}
        <div className={`flex items-center gap-2 px-3 py-1.5 rounded-xl ring-1 ${meta.bg} ${meta.ring} flex-shrink-0`}>
          <Zap size={13} className={meta.color} />
          <span className={`text-xs font-semibold tracking-wide uppercase ${meta.color}`}>{meta.label}</span>
        </div>

        {/* Quota bar */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-xs text-white/40">Builds used</span>
            <span className="text-xs font-medium text-white/60">
              {used}
              {stats.buildsAllowed < 9999 ? ` / ${stats.buildsAllowed}` : ' (unlimited)'}
            </span>
          </div>
          <div className="h-1.5 rounded-full bg-white/[0.06] overflow-hidden">
            <motion.div
              className={`h-full rounded-full ${quotaColor}`}
              initial={{ width: 0 }}
              animate={{ width: `${pct}%` }}
              transition={{ duration: 0.6, ease: 'easeOut' }}
            />
          </div>
        </div>

        {/* Project count */}
        <div className="flex items-center gap-3 flex-shrink-0 text-xs text-white/30">
          <span>{stats.totalProjects} project{stats.totalProjects !== 1 ? 's' : ''}</span>
          {stats.readyProjects > 0 && (
            <span className="text-accent-green">{stats.readyProjects} ready</span>
          )}
        </div>
      </div>

      {/* Expiry warning row */}
      {expiry.level !== 'ok' && ExpiryIcon && (
        <div className={`flex items-center gap-2 mt-3 px-3 py-2 rounded-xl border ${expiryStyle.bar}`}>
          <ExpiryIcon size={13} className={`${expiryStyle.text} flex-shrink-0`} />
          <span className={`text-xs ${expiryStyle.text}`}>
            {expiry.level === 'expired'
              ? `License expired · builds and new projects are blocked`
              : `License expires in ${expiry.daysLeft} day${expiry.daysLeft !== 1 ? 's' : ''} · upgrade to keep building`}
          </span>
        </div>
      )}
    </motion.div>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<ConversionProject[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingProject, setEditingProject] = useState<ConversionProject | null>(null);
  const [deletingProjectId, setDeletingProjectId] = useState<string | null>(null);
  const [generatedConfig, setGeneratedConfig] = useState<ElectronConfig | null>(null);
  const [formError, setFormError] = useState('');
  const [formLoading, setFormLoading] = useState(false);
  const [buildingIds, setBuildingIds] = useState<Set<string>>(new Set());
  const [buildProgress, setBuildProgress] = useState<Record<string, string>>({});
  const [buildLog, setBuildLog] = useState<Record<string, string>>({});
  const [buildError, setBuildError] = useState<Record<string, string>>({});
  const [expandedHistory, setExpandedHistory] = useState<Set<string>>(new Set());
  const [stats, setStats] = useState<ConversionStats | null>(null);
  const eventSources = useRef<Record<string, AbortController>>({});

  const fetchProjects = useCallback(async () => {
    try {
      const data = await conversionApi.list();
      setProjects(data);
    } catch {
      // silent
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchProjects(); }, [fetchProjects]);

  useEffect(() => {
    conversionApi.getStats().then(setStats).catch(() => {});
  }, []);

  const handleWizardSubmit = async (wizardData: WizardData) => {
    setFormError('');
    setFormLoading(true);
    try {
      const mc = wizardData.moduleConfig;
      const created = await conversionApi.create({
        projectName: wizardData.projectName,
        websiteUrl: wizardData.websiteUrl,
        appTitle: wizardData.appTitle,
        iconFile: wizardData.iconFile || undefined,
        enabledModules: wizardData.enabledModules.length > 0 ? wizardData.enabledModules : undefined,
        targetPlatform: wizardData.targetPlatform || undefined,
        moduleConfig: mc && Object.keys(mc).length > 0 ? mc : undefined,
      });
      setShowForm(false);
      setProjects(prev => [created, ...prev]);
      handleBuild(created.id);
    } catch (err: any) {
      setFormError(err.response?.data?.message || 'Failed to create project');
    } finally {
      setFormLoading(false);
    }
  };

  const handleUpdateSubmit = async (wizardData: WizardData) => {
    if (!editingProject) return;
    setFormError('');
    setFormLoading(true);
    try {
      const mc = wizardData.moduleConfig;
      const updated = await conversionApi.update(editingProject.id, {
        projectName: wizardData.projectName,
        websiteUrl: wizardData.websiteUrl,
        appTitle: wizardData.appTitle,
        iconFile: wizardData.iconFile || undefined,
        enabledModules: wizardData.enabledModules.length > 0 ? wizardData.enabledModules : undefined,
        targetPlatform: wizardData.targetPlatform || undefined,
        moduleConfig: mc && Object.keys(mc).length > 0 ? mc : undefined,
      });
      setEditingProject(null);
      fetchProjects();
      handleBuild(updated.id);
    } catch (err: any) {
      setFormError(err.response?.data?.message || 'Failed to update project');
    } finally {
      setFormLoading(false);
    }
  };

  const handleGenerate = async (id: string) => {
    try {
      const config = await conversionApi.generate(id);
      setGeneratedConfig(config);
      fetchProjects();
    } catch {
      // handle error
    }
  };

  const subscribeToBuild = useCallback((id: string) => {
    // Clear any existing connection
    if (eventSources.current[id]) {
      eventSources.current[id].abort();
    }

    const ctrl = new AbortController();
    eventSources.current[id] = ctrl;

    const cleanup = () => {
      ctrl.abort();
      delete eventSources.current[id];
      setBuildingIds(prev => { const next = new Set(prev); next.delete(id); return next; });
    };

    import('@microsoft/fetch-event-source').then(({ fetchEventSource }) => {
      const token = localStorage.getItem('accessToken');
      fetchEventSource(`/conversion/conversions/${id}/build/stream`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`
        },
        signal: ctrl.signal,
        onmessage(e) {
          if (e.event === 'progress') {
            try {
              const data = JSON.parse(e.data);
              if (data.progress) {
                setBuildProgress(prev => ({ ...prev, [id]: data.progress }));
                if (data.message) {
                  setBuildLog(prev => ({ ...prev, [id]: data.message }));
                }
              }
              if (data.progress === 'COMPLETE' || data.progress === 'FAILED') {
                if (data.progress === 'FAILED') {
                  setBuildError(prev => ({ ...prev, [id]: data.message || 'Build failed' }));
                }
                cleanup();
                fetchProjects();
              }
            } catch (err) {
              console.error('SSE Progress parse error', err);
            }
          } else if (e.event === 'status') {
            try {
              const data = JSON.parse(e.data);
              if (data.status === 'READY' || data.status === 'FAILED') {
                if (data.status === 'FAILED' && data.buildError) {
                  setBuildError(prev => ({ ...prev, [id]: data.buildError }));
                }
                cleanup();
                fetchProjects();
              } else if (data.buildProgress) {
                 setBuildProgress(prev => ({ ...prev, [id]: data.buildProgress }));
              }
            } catch (err) {
              console.error('SSE Status parse error', err);
            }
          }
        },
        onerror(err) {
          console.warn(`SSE connection error for project ${id}`, err);
          cleanup();
          fetchProjects();
          throw err; // Stop retrying
        }
      }).catch(err => {
        if (err.name !== 'AbortError') {
          console.error('fetchEventSource error', err);
        }
      });
    });
  }, [fetchProjects]);

  // Clean up SSE connections on unmount
  useEffect(() => {
    return () => {
      Object.values(eventSources.current).forEach(ctrl => ctrl.abort());
    };
  }, []);

  // Resume SSE for any projects that are BUILDING on load
  useEffect(() => {
    projects.forEach(p => {
      if (p.status === 'BUILDING' && !buildingIds.has(p.id) && !eventSources.current[p.id]) {
        setBuildingIds(prev => new Set(prev).add(p.id));
        if (p.buildProgress) {
          setBuildProgress(prev => ({ ...prev, [p.id]: p.buildProgress! }));
        }
        subscribeToBuild(p.id);
      }
    });
  }, [projects, buildingIds, subscribeToBuild]);

  const handleBuild = async (id: string) => {
    setBuildError(prev => { const next = { ...prev }; delete next[id]; return next; });
    setBuildingIds(prev => new Set(prev).add(id));
    try {
      await conversionApi.build(id);
      setBuildProgress(prev => ({ ...prev, [id]: 'PREPARING' }));
      fetchProjects();
      subscribeToBuild(id);
    } catch (err: any) {
      setBuildingIds(prev => { const next = new Set(prev); next.delete(id); return next; });
      setBuildError(prev => ({ ...prev, [id]: err.response?.data?.message || 'Build trigger failed' }));
    }
  };

  const handleDownload = (project: ConversionProject) => {
    // If the project has a direct R2 download URL, use it
    if (project.downloadUrl) {
      window.open(project.downloadUrl, '_blank');
    } else {
      // Fallback: use the gateway-proxied endpoint which 302-redirects to R2
      window.open(conversionApi.getDownloadUrl(project.id), '_blank');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await conversionApi.remove(id);
      setDeletingProjectId(null);
      fetchProjects();
    } catch {
      // handle error
    }
  };

  const downloadFile = (filename: string, content: string) => {
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  };

  const isExpired = stats ? computeExpiry(stats.licenseExpiresAt).isExpired : false;

  const statusColors: Record<string, string> = {
    DRAFT: 'bg-white/10 text-white/50',
    READY: 'bg-accent-green/10 text-accent-green',
    BUILDING: 'bg-accent-orange/10 text-accent-orange',
    FAILED: 'bg-red-500/10 text-red-400',
  };

  return (
    <div className="min-h-screen pt-24 pb-16 px-6">
      <div className="max-w-5xl mx-auto">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-10"
        >
          <div>
            <h1 className="text-3xl font-bold tracking-tight mb-1">
              Welcome, <span className="gradient-text-blue">{user?.username}</span>
            </h1>
            <p className="text-sm text-white/40">Manage your website-to-desktop conversions</p>
          </div>
          <button
            onClick={() => { if (!isExpired) { setShowForm(!showForm); setEditingProject(null); } }}
            disabled={isExpired}
            className={`btn-accent flex items-center gap-2 shadow-lg shadow-accent-blue/20 ${
              isExpired ? 'opacity-40 cursor-not-allowed' : ''
            }`}
            title={isExpired ? 'License expired — upgrade to create new projects' : undefined}
          >
            {showForm && !editingProject ? <X size={16} /> : <Plus size={16} />}
            {showForm && !editingProject ? 'Cancel' : 'New Conversion'}
          </button>
        </motion.div>

        {/* License Tier + Build Quota Banner */}
        {stats && <TierQuotaBanner stats={stats} />}

        {/* Quick Actions / Overview (aesthetic, removable later if desired) */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.05 }}
          className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8"
        >
          {[
            {
              title: 'Convert a URL',
              subtitle: 'Paste a site, get an app config.',
              icon: Rocket,
              accent: 'from-accent-blue/25 to-transparent',
              onClick: () => { setShowForm(true); setEditingProject(null); },
            },
            {
              title: 'Security presets',
              subtitle: 'Screenshot protection ready.',
              icon: Shield,
              accent: 'from-accent-violet/20 to-transparent',
              onClick: () => {},
            },
            {
              title: 'What’s new',
              subtitle: 'Refined glass + profile settings.',
              icon: Sparkles,
              accent: 'from-white/10 to-transparent',
              onClick: () => {},
            },
          ].map(({ title, subtitle, icon: Icon, accent, onClick }) => (
            <motion.button
              key={title}
              type="button"
              whileHover={{ y: -2 }}
              whileTap={{ scale: 0.99 }}
              onClick={onClick}
              className="glass-card p-5 sm:p-6 text-left relative overflow-hidden group"
            >
              <div className={`absolute inset-0 bg-gradient-to-br ${accent} opacity-0 group-hover:opacity-100 transition-opacity`} />
              <div className="relative flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <h3 className="text-base font-semibold tracking-tight mb-1">{title}</h3>
                  <p className="text-sm text-white/40 leading-relaxed">{subtitle}</p>
                </div>
                <div className="w-10 h-10 rounded-2xl bg-white/[0.04] border border-white/5 flex items-center justify-center flex-shrink-0">
                  <Icon size={18} className="text-white/60" />
                </div>
              </div>
            </motion.button>
          ))}
        </motion.div>

        {/* Create Form — wizard panel */}
        <AnimatePresence mode="wait">
          {(showForm || editingProject) && (
            <motion.div
              key="wizard"
              initial={{ opacity: 0, y: -12, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -12, scale: 0.98 }}
              transition={{ duration: 0.2 }}
              className="mb-8"
            >
              <div className="glass-card overflow-hidden">
                {/* Dark inner panel — wizard uses text-white/* classes throughout */}
                <div className="rounded-xl m-1" style={{ minHeight: 540 }}>
                  {formLoading ? (
                    <div className="flex flex-col items-center justify-center h-64 gap-3">
                      <Loader2 size={28} className="animate-spin text-indigo-600" />
                      <p className="text-sm text-gray-500">Creating project…</p>
                    </div>
                  ) : (
                    <ProjectWizard
                      devMode={true}
                      initialData={editingProject ? {
                        projectName: editingProject.projectName,
                        websiteUrl: editingProject.websiteUrl,
                        appTitle: editingProject.appTitle,
                        iconFile: editingProject.iconFile || '',
                        enabledModules: editingProject.enabledModules || [],
                        targetPlatform: (editingProject.targetPlatform as any) || 'auto',
                        moduleConfig: editingProject.moduleConfig ?? {},
                      } : undefined}
                      onSubmit={editingProject ? handleUpdateSubmit : handleWizardSubmit}
                      onCancel={() => { setShowForm(false); setEditingProject(null); setFormError(''); }}
                      submitLabel={editingProject ? "Update & Build" : "Create & Build"}
                    />
                  )}
                </div>
                {formError && (
                  <div className="flex items-center gap-2 px-4 pb-3">
                    <AlertCircle size={14} className="text-red-400 flex-shrink-0" />
                    <p className="text-xs text-red-300">{formError}</p>
                  </div>
                )}
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* License expired banner */}
        {isExpired && stats?.licenseExpiresAt && (
          <LicenseExpiredBanner expiresAt={stats.licenseExpiresAt} />
        )}

        {/* Projects List */}
        {loading ? (
          <div className="grid gap-4">
            {[0, 1, 2].map((i) => (
              <div key={i} className="glass-card p-5 sm:p-6">
                <div className="h-5 w-48 bg-white/5 rounded-lg animate-pulse mb-3" />
                <div className="h-4 w-72 bg-white/5 rounded-lg animate-pulse mb-2" />
                <div className="h-3 w-40 bg-white/5 rounded-lg animate-pulse" />
              </div>
            ))}
          </div>
        ) : projects.length === 0 ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-center py-20"
          >
            <Monitor size={48} className="mx-auto text-white/10 mb-4" />
            <p className="text-white/30 text-lg">No conversions yet</p>
            <p className="text-white/20 text-sm mt-1">Create your first website-to-desktop conversion above</p>
          </motion.div>
        ) : (
          <motion.div layout className="grid gap-4">
            {projects.map((project, i) => (
              <motion.div
                key={project.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.05 }}
                className="glass-card p-5 sm:p-6"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-3 mb-2">
                      <h3 className="text-lg font-semibold truncate">{project.appTitle}</h3>
                      <span className={`text-xs px-2.5 py-0.5 rounded-full font-medium ${statusColors[project.status]}`}>
                        {project.status}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 text-sm text-white/30">
                      <Globe size={13} />
                      <a href={project.websiteUrl} target="_blank" rel="noopener noreferrer"
                        className="hover:text-white/50 transition-colors truncate flex items-center gap-1">
                        {project.websiteUrl}
                        <ExternalLink size={11} />
                      </a>
                    </div>
                    <p className="text-xs text-white/20 mt-1">
                      v{project.currentVersion} · {project.projectName}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    {project.status === 'READY' && project.downloadAvailable && (
                      <button
                        onClick={() => handleDownload(project)}
                        className="btn-accent !py-2 !px-4 !text-xs flex items-center gap-1.5 shadow-md shadow-accent-blue/20"
                      >
                        <FileDown size={13} /> Download
                      </button>
                    )}
                    {buildingIds.has(project.id) || project.status === 'BUILDING' ? (
                      <button
                        disabled
                        className="btn-ghost !py-2 !px-4 !text-xs flex items-center gap-1.5 opacity-80 cursor-wait relative overflow-hidden"
                      >
                        <div className="absolute inset-0 bg-accent-blue/10 animate-pulse" />
                        <Loader2 size={13} className="animate-spin text-accent-blue relative z-10" />
                        <span className="relative z-10 text-accent-blue font-medium truncate max-w-[200px]" title={buildProgress[project.id] === 'BUILD_LOG' ? buildLog[project.id] : ''}>
                          {buildProgress[project.id] === 'PREPARING' ? 'Preparing...' :
                           buildProgress[project.id] === 'CLONING' ? 'Cloning repo...' :
                           buildProgress[project.id] === 'WRITING_FILES' ? 'Configuring...' :
                           buildProgress[project.id] === 'INSTALLING' ? 'Installing deps...' :
                           buildProgress[project.id] === 'BUILDING' ? 'Building exe...' :
                           buildProgress[project.id] === 'FINDING_ARTIFACT' ? 'Locating artifact...' :
                           buildProgress[project.id] === 'UPLOADING_R2' ? 'Uploading...' :
                           buildProgress[project.id] === 'BUILD_LOG' ? (buildLog[project.id] || 'Building...') :
                           (project.buildProgress || 'Building...')}
                        </span>
                      </button>
                    ) : (
                      <button
                        onClick={() => { if (!isExpired) { setEditingProject(project); window.scrollTo({ top: 0, behavior: 'smooth' }); } }}
                        disabled={isExpired}
                        className={`btn-ghost !py-2 !px-4 !text-xs flex items-center gap-1.5 hover:bg-white/10 ${
                          isExpired ? 'opacity-40 cursor-not-allowed' : ''
                        }`}
                        title={isExpired ? 'License expired — upgrade to build' : undefined}
                      >
                        <Package size={13} /> Build
                      </button>
                    )}

                    <button
                      onClick={() => setExpandedHistory(prev => {
                        const next = new Set(prev);
                        next.has(project.id) ? next.delete(project.id) : next.add(project.id);
                        return next;
                      })}
                      className={`p-2 rounded-xl transition-all ${
                        expandedHistory.has(project.id)
                          ? 'bg-accent-blue/10 text-accent-blue'
                          : 'text-white/20 hover:text-white/50 hover:bg-white/5'
                      }`}
                      title="Build history"
                    >
                      <History size={16} />
                    </button>
                    
                    {deletingProjectId === project.id ? (
                      <div className="flex items-center gap-1 ml-1">
                        <button
                          onClick={() => setDeletingProjectId(null)}
                          className="px-2 py-1.5 rounded-lg text-xs font-medium text-white/50 hover:bg-white/10 transition-colors"
                        >Cancel</button>
                        <button
                          onClick={() => handleDelete(project.id)}
                          className="px-2 py-1.5 rounded-lg text-xs font-semibold bg-red-500/20 text-red-400 hover:bg-red-500/30 transition-colors"
                        >Confirm</button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setDeletingProjectId(project.id)}
                        className="p-2 rounded-xl text-white/20 hover:text-red-400 hover:bg-red-500/10 transition-all"
                        title="Delete project"
                      >
                        <Trash2 size={16} />
                      </button>
                    )}
                  </div>
                </div>
                {/* Build error message */}
                {(buildError[project.id] || (project.status === 'FAILED' && project.buildError)) && (
                  <div className="flex items-center gap-2 mt-3 p-2.5 rounded-lg bg-red-500/10 border border-red-500/20">
                    <AlertCircle size={14} className="text-red-400 flex-shrink-0" />
                    <p className="text-xs text-red-300">{buildError[project.id] || project.buildError}</p>
                  </div>
                )}
                {/* Build history panel */}
                {expandedHistory.has(project.id) && (
                  <div className="mt-4 pt-4 border-t border-white/[0.04]">
                    <BuildDashboard 
                      projectId={project.id} 
                      projectName={project.projectName} 
                      onRebuild={(record) => {
                        setEditingProject({
                          ...project,
                          enabledModules: record.enabledModules || [],
                          targetPlatform: record.buildTarget as any || 'win'
                        });
                        setExpandedHistory(prev => {
                          const next = new Set(prev);
                          next.delete(project.id);
                          return next;
                        });
                        window.scrollTo({ top: 0, behavior: 'smooth' });
                      }}
                    />
                  </div>
                )}
              </motion.div>
            ))}
          </motion.div>
        )}

        {/* Generated Config Modal */}
        <AnimatePresence>
          {generatedConfig && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/70 backdrop-blur-sm"
              onClick={() => setGeneratedConfig(null)}
            >
              <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                transition={{ type: 'spring', stiffness: 200, damping: 25 }}
                className="glass-card p-6 sm:p-8 max-w-2xl w-full max-h-[85vh] overflow-y-auto"
                onClick={(e) => e.stopPropagation()}
              >
                <div className="flex items-center justify-between mb-6">
                  <div>
                    <h2 className="text-xl font-bold flex items-center gap-2">
                      <CheckCircle2 size={20} className="text-accent-green" />
                      Generated: {generatedConfig.appTitle}
                    </h2>
                    <p className="text-sm text-white/40 mt-1">Download each file to build your desktop app</p>
                  </div>
                  <button onClick={() => setGeneratedConfig(null)} className="p-2 rounded-xl hover:bg-white/5">
                    <X size={18} className="text-white/40" />
                  </button>
                </div>

                <div className="space-y-3">
                  {Object.entries(generatedConfig.files).map(([filename, content]) => (
                    <div key={filename} className="bg-white/[0.03] rounded-xl border border-white/5 overflow-hidden">
                      <div className="flex items-center justify-between px-4 py-3 border-b border-white/5">
                        <span className="text-sm font-mono text-white/60">{filename}</span>
                        <button
                          onClick={() => downloadFile(filename, content)}
                          className="text-xs text-accent-blue hover:underline flex items-center gap-1"
                        >
                          <Download size={12} /> Download
                        </button>
                      </div>
                      <pre className="p-4 text-xs text-white/40 overflow-x-auto max-h-48 font-mono leading-relaxed">
                        {content.slice(0, 500)}{content.length > 500 ? '\n...' : ''}
                      </pre>
                    </div>
                  ))}
                </div>

                <button
                  onClick={() => {
                    Object.entries(generatedConfig.files).forEach(([f, c]) => downloadFile(f, c));
                  }}
                  className="btn-accent w-full mt-6 flex items-center justify-center gap-2"
                >
                  <Download size={16} /> Download All Files
                </button>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
