import { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Plus, Globe, Trash2, Loader2,
  ExternalLink, X, AlertCircle, CheckCircle2,
  Package, FileDown, History, Zap, Lock, TriangleAlert,
  ChevronRight, GitBranch, Activity
} from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { conversionApi } from '../services/api';
import type { ConversionProject, ConversionStats, ElectronConfig, LicenseTier } from '../types';
import ProjectWizard, { type WizardData } from '../components/ProjectWizard';
import BuildDashboard from '../components/BuildDashboard';

// ── Animations ──────────────────────────────────────────
const stagger = {
  hidden: {},
  show: { transition: { staggerChildren: 0.04 } },
};
const fadeUp = {
  hidden: { opacity: 0, y: 12 },
  show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.25, 0.1, 0.25, 1] } },
};

// ── Expiry helpers ──────────────────────────────────────

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
  info:     { bar: 'bg-accent-blue/8 border-accent-blue/15',  text: 'text-accent-blue',  icon: TriangleAlert },
  warning:  { bar: 'bg-accent-orange/8 border-accent-orange/15', text: 'text-accent-orange', icon: TriangleAlert },
  critical: { bar: 'bg-red-500/8 border-red-500/15',          text: 'text-red-400',      icon: AlertCircle },
  expired:  { bar: 'bg-red-500/10 border-red-500/20',         text: 'text-red-400',      icon: Lock },
} as const;

const TIER_META: Record<LicenseTier, { label: string; color: string; bg: string; border: string }> = {
  TRIAL:    { label: 'Trial',    color: 'text-white/50',      bg: 'bg-white/[0.04]',     border: 'border-white/10' },
  STARTER:  { label: 'Starter',  color: 'text-accent-blue',   bg: 'bg-accent-blue/8',    border: 'border-accent-blue/20' },
  PRO:      { label: 'Pro',      color: 'text-accent-violet', bg: 'bg-accent-violet/8',  border: 'border-accent-violet/20' },
  LIFETIME: { label: 'Lifetime', color: 'text-accent-green',  bg: 'bg-accent-green/8',   border: 'border-accent-green/20' },
};

// ── Sub-components ──────────────────────────────────────

function TierQuotaBanner({ stats }: { stats: ConversionStats }) {
  const meta = TIER_META[stats.tier] ?? TIER_META.TRIAL;
  const used = stats.buildsAllowed - stats.buildsRemaining;
  const pct = stats.buildsAllowed > 0 ? Math.min(100, (used / stats.buildsAllowed) * 100) : 0;
  const quotaColor = pct >= 90 ? 'bg-red-500' : pct >= 70 ? 'bg-accent-orange' : 'bg-accent-blue';
  const expiry = computeExpiry(stats.licenseExpiresAt);
  const expiryStyle = EXPIRY_STYLE[expiry.level];
  const ExpiryIcon = expiryStyle.icon;

  return (
    <motion.div variants={fadeUp} className="glass-card p-4 mb-6">
      <div className="flex flex-col sm:flex-row sm:items-center gap-4">
        {/* Tier badge */}
        <div className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border ${meta.bg} ${meta.border} flex-shrink-0`}>
          <Zap size={12} className={meta.color} />
          <span className={`text-[11px] font-bold tracking-wider uppercase ${meta.color}`}>{meta.label}</span>
        </div>

        {/* Quota bar */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-[11px] text-white/35 font-medium">Builds used</span>
            <span className="text-[11px] font-semibold text-white/50">
              {used}
              {stats.buildsAllowed < 9999 ? ` / ${stats.buildsAllowed}` : ' (unlimited)'}
            </span>
          </div>
          <div className="h-1 rounded-full bg-white/[0.06] overflow-hidden">
            <motion.div
              className={`h-full rounded-full ${quotaColor}`}
              initial={{ width: 0 }}
              animate={{ width: `${pct}%` }}
              transition={{ duration: 0.6, ease: 'easeOut' }}
            />
          </div>
        </div>

        {/* Project count */}
        <div className="flex items-center gap-3 flex-shrink-0 text-[11px] text-white/30 font-medium">
          <span>{stats.totalProjects} project{stats.totalProjects !== 1 ? 's' : ''}</span>
          {stats.readyProjects > 0 && (
            <span className="text-accent-green">{stats.readyProjects} ready</span>
          )}
        </div>
      </div>

      {/* Expiry warning */}
      {expiry.level !== 'ok' && ExpiryIcon && (
        <div className={`flex items-center gap-2 mt-3 px-3 py-2 rounded-lg border ${expiryStyle.bar}`}>
          <ExpiryIcon size={12} className={`${expiryStyle.text} flex-shrink-0`} />
          <span className={`text-[11px] ${expiryStyle.text}`}>
            {expiry.level === 'expired'
              ? 'License expired — builds and new projects are blocked'
              : `License expires in ${expiry.daysLeft} day${expiry.daysLeft !== 1 ? 's' : ''} — upgrade to keep building`}
          </span>
        </div>
      )}
    </motion.div>
  );
}

const STATUS_CONFIG: Record<string, { bg: string; text: string; dot: string }> = {
  DRAFT:    { bg: 'bg-white/[0.06]', text: 'text-white/50', dot: 'bg-white/30' },
  READY:    { bg: 'bg-accent-green/10', text: 'text-accent-green', dot: 'bg-accent-green' },
  BUILDING: { bg: 'bg-accent-orange/10', text: 'text-accent-orange', dot: 'bg-accent-orange' },
  FAILED:   { bg: 'bg-red-500/10', text: 'text-red-400', dot: 'bg-red-400' },
};

function StatusBadge({ status }: { status: string }) {
  const cfg = STATUS_CONFIG[status] ?? STATUS_CONFIG.DRAFT;
  return (
    <span className={`inline-flex items-center gap-1.5 text-[10px] px-2.5 py-1 rounded-md font-bold tracking-wider uppercase ${cfg.bg} ${cfg.text}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${cfg.dot}`} />
      {status}
    </span>
  );
}

function ProjectCard({
  project,
  isBuilding,
  buildProgress: progress,
  buildLog: logMsg,
  buildError: error,
  isExpired,
  expandedHistory,
  onToggleHistory,
  onBuild,
  onDownload,
  onDelete,
  onRebuild,
  deletingId,
  onConfirmDelete,
  onCancelDelete,
}: {
  project: ConversionProject;
  isBuilding: boolean;
  buildProgress?: string;
  buildLog?: string;
  buildError?: string;
  isExpired: boolean;
  expandedHistory: boolean;
  onToggleHistory: () => void;
  onBuild: () => void;
  onDownload: () => void;
  onDelete: () => void;
  onRebuild: (record: any) => void;
  deletingId: boolean;
  onConfirmDelete: () => void;
  onCancelDelete: () => void;
}) {
  const building = isBuilding || project.status === 'BUILDING';
  const buildProgressLabel = (() => {
    if (!progress) return project.buildProgress || 'Building...';
    switch (progress) {
      case 'PREPARING': return 'Preparing…';
      case 'CLONING': return 'Cloning repo…';
      case 'WRITING_FILES': return 'Configuring…';
      case 'INSTALLING': return 'Installing deps…';
      case 'BUILDING': return 'Building exe…';
      case 'FINDING_ARTIFACT': return 'Locating artifact…';
      case 'UPLOADING_R2': return 'Uploading…';
      case 'BUILD_LOG': return logMsg || 'Building…';
      default: return progress;
    }
  })();

  return (
    <motion.div variants={fadeUp} className="glass-card overflow-hidden">
      <div className="p-5">
        {/* Top row */}
        <div className="flex items-start justify-between gap-4 mb-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2.5 mb-1">
              <h3 className="text-[15px] font-bold tracking-tight truncate">{project.appTitle}</h3>
              <StatusBadge status={project.status} />
            </div>
            <div className="flex items-center gap-1.5 text-[12px] text-white/30">
              <Globe size={11} className="flex-shrink-0" />
              <a href={project.websiteUrl} target="_blank" rel="noopener noreferrer"
                className="hover:text-white/50 transition-colors truncate flex items-center gap-1">
                {project.websiteUrl.replace(/^https?:\/\//, '')}
                <ExternalLink size={9} className="flex-shrink-0 opacity-50" />
              </a>
            </div>
          </div>

          {/* Action buttons — grouped tightly */}
          <div className="flex items-center gap-1.5 flex-shrink-0">
            {project.status === 'READY' && project.downloadAvailable && (
              <button onClick={onDownload}
                className="btn-accent !py-2 !px-3.5 !text-[11px] !rounded-lg flex items-center gap-1.5">
                <FileDown size={12} /> Download
              </button>
            )}

            {building ? (
              <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-accent-blue/8 border border-accent-blue/15">
                <Loader2 size={12} className="animate-spin text-accent-blue" />
                <span className="text-[11px] text-accent-blue font-medium truncate max-w-[160px]">{buildProgressLabel}</span>
              </div>
            ) : (
              <button onClick={onBuild} disabled={isExpired}
                className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-[11px] font-medium border transition-all
                  ${isExpired
                    ? 'border-white/5 text-white/20 cursor-not-allowed'
                    : 'border-white/8 text-white/60 hover:text-white hover:bg-white/[0.04] hover:border-white/12'}`}>
                <Package size={12} /> Build
              </button>
            )}

            <button onClick={onToggleHistory}
              className={`p-2 rounded-lg transition-all ${expandedHistory
                ? 'bg-accent-blue/10 text-accent-blue border border-accent-blue/20'
                : 'text-white/25 hover:text-white/50 hover:bg-white/[0.04] border border-transparent'}`}
              title="Build history">
              <History size={14} />
            </button>

            {deletingId ? (
              <div className="flex items-center gap-1">
                <button onClick={onCancelDelete}
                  className="px-2.5 py-1.5 rounded-lg text-[11px] font-medium text-white/40 hover:bg-white/[0.04] transition-colors">
                  Cancel
                </button>
                <button onClick={onConfirmDelete}
                  className="px-2.5 py-1.5 rounded-lg text-[11px] font-bold bg-red-500/15 text-red-400 hover:bg-red-500/25 transition-colors">
                  Delete
                </button>
              </div>
            ) : (
              <button onClick={onDelete}
                className="p-2 rounded-lg text-white/20 hover:text-red-400 hover:bg-red-500/8 transition-all border border-transparent"
                title="Delete project">
                <Trash2 size={14} />
              </button>
            )}
          </div>
        </div>

        {/* Version info row */}
        <div className="flex items-center gap-3 text-[11px]">
          <span className="flex items-center gap-1.5 text-white/25">
            <GitBranch size={10} />
            v{project.currentVersion}
          </span>
          <span className="text-white/10">·</span>
          <span className="text-white/20 font-mono text-[10px]">{project.projectName}</span>
          {project.enabledModules && project.enabledModules.length > 0 && (
            <>
              <span className="text-white/10">·</span>
              <span className="text-white/25">{project.enabledModules.length} module{project.enabledModules.length !== 1 ? 's' : ''}</span>
            </>
          )}
        </div>
      </div>

      {/* Build error */}
      {(error || (project.status === 'FAILED' && project.buildError)) && (
        <div className="mx-5 mb-4 flex items-start gap-2 p-3 rounded-lg bg-red-500/8 border border-red-500/15">
          <AlertCircle size={12} className="text-red-400 flex-shrink-0 mt-0.5" />
          <p className="text-[11px] text-red-300 leading-relaxed">{error || project.buildError}</p>
        </div>
      )}

      {/* Build history panel */}
      <AnimatePresence>
        {expandedHistory && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.25, ease: [0.25, 0.1, 0.25, 1] }}
            className="overflow-hidden"
          >
            <div className="px-5 pb-5 border-t border-white/[0.04]">
              <BuildDashboard
                projectId={project.id}
                projectName={project.projectName}
                onRebuild={onRebuild}
              />
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

// ── Main Dashboard ──────────────────────────────────────

export default function DashboardPage() {
  const { user } = useAuth();
  const wizardRef = useRef<HTMLDivElement>(null);
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
    } catch { /* silent */ }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchProjects(); }, [fetchProjects]);
  useEffect(() => { conversionApi.getStats().then(setStats).catch(() => {}); }, []);

  // ── Wizard handlers ───────────────────────────────────

  const openWizard = (project?: ConversionProject) => {
    if (project) {
      setEditingProject(project);
      setShowForm(false);
    } else {
      setShowForm(true);
      setEditingProject(null);
    }
    // Smooth scroll to wizard, no jarring jump
    requestAnimationFrame(() => {
      wizardRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  };

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

  // ── Build / SSE handlers ──────────────────────────────

  const subscribeToBuild = useCallback((id: string) => {
    if (eventSources.current[id]) eventSources.current[id].abort();
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
        headers: { 'Authorization': `Bearer ${token}` },
        signal: ctrl.signal,
        onmessage(e) {
          if (e.event === 'progress') {
            try {
              const data = JSON.parse(e.data);
              if (data.progress) {
                setBuildProgress(prev => ({ ...prev, [id]: data.progress }));
                if (data.message) setBuildLog(prev => ({ ...prev, [id]: data.message }));
              }
              if (data.progress === 'COMPLETE' || data.progress === 'FAILED') {
                if (data.progress === 'FAILED') setBuildError(prev => ({ ...prev, [id]: data.message || 'Build failed' }));
                cleanup();
                fetchProjects();
              }
            } catch (_) {}
          } else if (e.event === 'status') {
            try {
              const data = JSON.parse(e.data);
              if (data.status === 'READY' || data.status === 'FAILED') {
                if (data.status === 'FAILED' && data.buildError) setBuildError(prev => ({ ...prev, [id]: data.buildError }));
                cleanup();
                fetchProjects();
              } else if (data.buildProgress) {
                setBuildProgress(prev => ({ ...prev, [id]: data.buildProgress }));
              }
            } catch (_) {}
          }
        },
        onerror(err) {
          cleanup();
          fetchProjects();
          throw err;
        },
      }).catch(err => {
        if (err.name !== 'AbortError') console.error('SSE error', err);
      });
    });
  }, [fetchProjects]);

  useEffect(() => () => {
    Object.values(eventSources.current).forEach(ctrl => ctrl.abort());
  }, []);

  useEffect(() => {
    projects.forEach(p => {
      if (p.status === 'BUILDING' && !buildingIds.has(p.id) && !eventSources.current[p.id]) {
        setBuildingIds(prev => new Set(prev).add(p.id));
        if (p.buildProgress) setBuildProgress(prev => ({ ...prev, [p.id]: p.buildProgress! }));
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
    if (project.downloadUrl) window.open(project.downloadUrl, '_blank');
    else window.open(conversionApi.getDownloadUrl(project.id), '_blank');
  };

  const handleDelete = async (id: string) => {
    try {
      await conversionApi.remove(id);
      setDeletingProjectId(null);
      fetchProjects();
    } catch { /* handle */ }
  };

  const isExpired = stats ? computeExpiry(stats.licenseExpiresAt).isExpired : false;

  // ── Render ────────────────────────────────────────────

  return (
    <div className="min-h-screen pt-24 pb-16 px-6">
      <motion.div className="max-w-5xl mx-auto" initial="hidden" animate="show" variants={stagger}>

        {/* ── Header ──────────────────────────────────── */}
        <motion.div variants={fadeUp} className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-8">
          <div>
            <h1 className="text-2xl font-bold tracking-tight mb-1">
              Welcome back, <span className="gradient-text-blue">{user?.username}</span>
            </h1>
            <p className="text-[13px] text-white/35">Manage your website-to-desktop conversions</p>
          </div>
          <button
            onClick={() => { if (!isExpired) openWizard(); }}
            disabled={isExpired}
            className={`btn-accent flex items-center gap-2 !px-5 !py-2.5 ${isExpired ? 'opacity-30 cursor-not-allowed' : ''}`}
          >
            {showForm && !editingProject ? <X size={14} /> : <Plus size={14} />}
            {showForm && !editingProject ? 'Cancel' : 'New Conversion'}
          </button>
        </motion.div>

        {/* ── Tier + Quota ────────────────────────────── */}
        {stats && <TierQuotaBanner stats={stats} />}

        {/* ── Quick Stats Row ─────────────────────────── */}
        <motion.div variants={fadeUp} className="grid grid-cols-3 gap-3 mb-8">
          {[
            { label: 'Total Projects', value: stats?.totalProjects ?? 0, icon: Activity },
            { label: 'Ready to Ship', value: stats?.readyProjects ?? 0, icon: CheckCircle2, accent: 'text-accent-green' },
            { label: 'Builds Remaining', value: stats?.buildsRemaining ?? 0, icon: Package, accent: 'text-accent-blue' },
          ].map(({ label, value, icon: Icon, accent }) => (
            <div key={label} className="glass-card p-4 flex items-center gap-3">
              <div className="w-9 h-9 rounded-lg bg-white/[0.04] border border-white/[0.06] flex items-center justify-center flex-shrink-0">
                <Icon size={16} className={accent || 'text-white/40'} />
              </div>
              <div>
                <p className="text-[20px] font-bold tracking-tight leading-none">{value}</p>
                <p className="text-[10px] text-white/30 font-medium uppercase tracking-wider mt-0.5">{label}</p>
              </div>
            </div>
          ))}
        </motion.div>

        {/* ── Wizard Panel ────────────────────────────── */}
        <AnimatePresence mode="wait">
          {(showForm || editingProject) && (
            <motion.div
              ref={wizardRef}
              key="wizard"
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.3, ease: [0.25, 0.1, 0.25, 1] }}
              className="mb-8 overflow-hidden"
            >
              <div className="glass-card overflow-hidden">
                <div className="rounded-xl m-1" style={{ minHeight: 520 }}>
                  {formLoading ? (
                    <div className="flex flex-col items-center justify-center h-64 gap-3">
                      <Loader2 size={24} className="animate-spin text-accent-blue" />
                      <p className="text-[13px] text-white/40">Creating project…</p>
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
                      submitLabel={editingProject ? 'Update & Build' : 'Create & Build'}
                    />
                  )}
                </div>
                {formError && (
                  <div className="flex items-center gap-2 px-4 pb-3">
                    <AlertCircle size={12} className="text-red-400 flex-shrink-0" />
                    <p className="text-[11px] text-red-300">{formError}</p>
                  </div>
                )}
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* ── License Expired ─────────────────────────── */}
        {isExpired && stats?.licenseExpiresAt && (
          <motion.div variants={fadeUp}
            className="flex items-start gap-3 p-4 rounded-xl mb-6 bg-red-500/8 border border-red-500/15">
            <Lock size={14} className="text-red-400 flex-shrink-0 mt-0.5" />
            <div className="flex-1 min-w-0">
              <p className="text-[13px] font-semibold text-red-300">License expired</p>
              <p className="text-[11px] text-red-400/70 mt-0.5">
                Your license expired on {new Date(stats.licenseExpiresAt).toLocaleDateString()}.
                New builds and project creation are blocked.
                {' '}<span className="text-red-300 underline cursor-pointer">Upgrade to continue →</span>
              </p>
            </div>
          </motion.div>
        )}

        {/* ── Projects List ───────────────────────────── */}
        <motion.div variants={fadeUp}>
          <div className="flex items-center gap-2 mb-4">
            <h2 className="text-[14px] font-bold text-white/70 tracking-tight">Your Projects</h2>
            <ChevronRight size={12} className="text-white/20" />
          </div>
        </motion.div>

        {loading ? (
          <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-3">
            {[0, 1, 2].map(i => (
              <motion.div key={i} variants={fadeUp} className="glass-card p-5">
                <div className="h-4 w-48 bg-white/[0.04] rounded-lg animate-pulse mb-3" />
                <div className="h-3 w-72 bg-white/[0.04] rounded-lg animate-pulse mb-2" />
                <div className="h-2.5 w-40 bg-white/[0.04] rounded-lg animate-pulse" />
              </motion.div>
            ))}
          </motion.div>
        ) : projects.length === 0 ? (
          <motion.div variants={fadeUp} className="text-center py-20">
            <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-white/[0.03] border border-white/[0.06] flex items-center justify-center">
              <Package size={28} className="text-white/15" />
            </div>
            <p className="text-white/30 text-[15px] font-medium">No conversions yet</p>
            <p className="text-white/20 text-[12px] mt-1">Create your first website-to-desktop conversion above</p>
          </motion.div>
        ) : (
          <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-3">
            {projects.map(project => (
              <ProjectCard
                key={project.id}
                project={project}
                isBuilding={buildingIds.has(project.id)}
                buildProgress={buildProgress[project.id]}
                buildLog={buildLog[project.id]}
                buildError={buildError[project.id]}
                isExpired={isExpired}
                expandedHistory={expandedHistory.has(project.id)}
                onToggleHistory={() => setExpandedHistory(prev => {
                  const next = new Set(prev);
                  next.has(project.id) ? next.delete(project.id) : next.add(project.id);
                  return next;
                })}
                onBuild={() => openWizard(project)}
                onDownload={() => handleDownload(project)}
                onDelete={() => setDeletingProjectId(project.id)}
                deletingId={deletingProjectId === project.id}
                onConfirmDelete={() => handleDelete(project.id)}
                onCancelDelete={() => setDeletingProjectId(null)}
                onRebuild={(record) => {
                  setEditingProject({
                    ...project,
                    enabledModules: record.enabledModules || [],
                    targetPlatform: record.buildTarget as any || 'win',
                  });
                  setExpandedHistory(prev => { const next = new Set(prev); next.delete(project.id); return next; });
                  requestAnimationFrame(() => {
                    wizardRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                  });
                }}
              />
            ))}
          </motion.div>
        )}

        {/* ── Generated Config Modal ──────────────────── */}
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
                initial={{ opacity: 0, scale: 0.96, y: 8 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.96, y: 8 }}
                transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                className="glass-card p-6 sm:p-8 max-w-2xl w-full max-h-[85vh] overflow-y-auto"
                onClick={e => e.stopPropagation()}
              >
                <div className="flex items-center justify-between mb-6">
                  <div>
                    <h2 className="text-lg font-bold flex items-center gap-2">
                      <CheckCircle2 size={18} className="text-accent-green" />
                      Generated: {generatedConfig.appTitle}
                    </h2>
                    <p className="text-[12px] text-white/35 mt-1">Download each file to build your desktop app</p>
                  </div>
                  <button onClick={() => setGeneratedConfig(null)} className="p-2 rounded-lg hover:bg-white/[0.04]">
                    <X size={16} className="text-white/40" />
                  </button>
                </div>

                <div className="space-y-2.5">
                  {Object.entries(generatedConfig.files).map(([filename, content]) => (
                    <div key={filename} className="bg-white/[0.02] rounded-xl border border-white/[0.06] overflow-hidden">
                      <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/[0.04]">
                        <span className="text-[12px] font-mono text-white/50">{filename}</span>
                        <button onClick={() => {
                          const blob = new Blob([content], { type: 'text/plain' });
                          const url = URL.createObjectURL(blob);
                          const a = document.createElement('a');
                          a.href = url; a.download = filename; a.click();
                          URL.revokeObjectURL(url);
                        }} className="text-[11px] text-accent-blue hover:underline flex items-center gap-1">
                          <FileDown size={10} /> Download
                        </button>
                      </div>
                      <pre className="p-4 text-[11px] text-white/30 overflow-x-auto max-h-40 font-mono leading-relaxed">
                        {content.slice(0, 500)}{content.length > 500 ? '\n...' : ''}
                      </pre>
                    </div>
                  ))}
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    </div>
  );
}
