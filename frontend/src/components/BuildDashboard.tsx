import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  CheckCircle2, XCircle, Clock, Package, ChevronDown, ChevronUp,
  RefreshCw, GitCommit, List, GitBranch
} from 'lucide-react';
import { conversionApi } from '../services/api';

// ─── Types ───────────────────────────────────────────

interface BuildRecord {
  id: string;
  projectId: string;
  projectName: string;
  result: string;
  buildError?: string;
  artifactUrl?: string;
  buildTarget: string;
  enabledModules: string[];
  startedAt: string;
  completedAt: string;
  durationMs: number;
}

interface BuildMetrics {
  totalBuilds: number;
  successfulBuilds: number;
  failedBuilds: number;
  avgDurationMs: number;
  successRate: number;
}

interface BuildDashboardProps {
  projectId: string;
  projectName: string;
  onRebuild?: (record: BuildRecord) => void;
}

// ─── Helpers ─────────────────────────────────────────

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  const s = Math.round(ms / 1000);
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  const rem = s % 60;
  return `${m}m ${rem}s`;
}

function formatDate(iso: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatRelative(iso: string): string {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  return `${days}d ago`;
}

// ─── Stat Card ───────────────────────────────────────

function StatCard({ label, value, accent }: {
  label: string; value: string | number; accent?: string;
}) {
  return (
    <div className="flex flex-col gap-0.5 rounded-xl border border-white/[0.05] bg-white/[0.015] px-3 py-2.5">
      <span className="text-[9px] font-bold text-white/25 uppercase tracking-widest">{label}</span>
      <span className={`text-lg font-bold tracking-tight leading-none ${accent ?? 'text-white/80'}`}>{value}</span>
    </div>
  );
}

// ─── Version Timeline ─────────────────────────────────

function VersionTimeline({ records, onRebuild }: { records: BuildRecord[]; onRebuild?: (record: BuildRecord) => void }) {
  if (records.length === 0) return null;

  return (
    <div className="relative">
      {records.map((record, idx) => {
        const success = record.result === 'READY';
        const isLast = idx === records.length - 1;

        return (
          <div key={record.id} className="relative flex gap-3 group">
            {/* Timeline column */}
            <div className="flex flex-col items-center flex-shrink-0 w-6">
              {/* Dot */}
              <div className={`relative z-10 w-3 h-3 rounded-full border-2 flex-shrink-0 mt-1 transition-colors ${
                success
                  ? 'border-[#00C896] bg-[#00C896]/20'
                  : 'border-red-400 bg-red-400/20'
              }`}>
                <div className={`absolute inset-[2px] rounded-full ${
                  success ? 'bg-[#00C896]' : 'bg-red-400'
                }`} />
              </div>
              {/* Line */}
              {!isLast && (
                <div className="w-px flex-1 bg-white/[0.06] min-h-[24px]" />
              )}
            </div>

            {/* Content */}
            <div className={`flex-1 pb-4 ${isLast ? '' : ''}`}>
              <div className="flex items-center gap-2 flex-wrap">
                {/* Status + target */}
                <span className={`text-[11px] font-bold tracking-wider uppercase ${
                  success ? 'text-[#00C896]' : 'text-red-400'
                }`}>
                  {success ? 'Success' : 'Failed'}
                </span>
                <span className="text-[10px] text-white/25 font-mono uppercase">{record.buildTarget}</span>
                <span className="text-[10px] text-white/15">·</span>
                <span className="text-[10px] text-white/25">{formatRelative(record.completedAt)}</span>
              </div>

              {/* Meta row */}
              <div className="flex items-center gap-3 mt-1 text-[10px] text-white/20">
                <span className="flex items-center gap-1">
                  <Clock size={9} />
                  {formatDuration(record.durationMs)}
                </span>
                <span>{formatDate(record.completedAt)}</span>
                {record.enabledModules?.length > 0 && (
                  <span>{record.enabledModules.length} module{record.enabledModules.length !== 1 ? 's' : ''}</span>
                )}
              </div>

              {/* Error */}
              {record.buildError && (
                <div className="mt-2 flex items-start gap-1.5 p-2 rounded-lg bg-red-500/6 border border-red-500/10 max-w-lg">
                  <XCircle size={10} className="text-red-400 mt-0.5 flex-shrink-0" />
                  <span className="text-[10px] text-red-300/80 leading-relaxed break-all">{record.buildError}</span>
                </div>
              )}

              {/* Actions */}
              <div className="flex items-center gap-2 mt-2 opacity-0 group-hover:opacity-100 transition-opacity">
                {record.artifactUrl && (
                  <a
                    href={record.artifactUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-[10px] text-[#6C63FF] hover:underline"
                  >
                    Download artifact
                  </a>
                )}
                {onRebuild && (
                  <button
                    type="button"
                    onClick={() => onRebuild(record)}
                    className="text-[10px] text-white/30 hover:text-white/60 transition-colors"
                  >
                    Rebuild with this config
                  </button>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ─── Build Row (list view) ────────────────────────────

function BuildRow({ record, onRebuild }: { record: BuildRecord; onRebuild?: (record: BuildRecord) => void; }) {
  const [expanded, setExpanded] = useState(false);
  const success = record.result === 'READY';

  return (
    <div className="border border-white/[0.05] rounded-xl overflow-hidden">
      <button
        type="button"
        onClick={() => setExpanded(e => !e)}
        className="w-full flex items-center gap-3 px-3.5 py-2.5 text-left hover:bg-white/[0.015] transition-colors"
      >
        <div className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${success ? 'bg-[#00C896]' : 'bg-red-400'}`} />
        <span className={`text-[10px] font-bold flex-shrink-0 w-12 uppercase tracking-wider ${success ? 'text-[#00C896]' : 'text-red-400'}`}>
          {success ? 'Pass' : 'Fail'}
        </span>
        <span className="text-[10px] text-white/35 flex-shrink-0 font-mono uppercase">{record.buildTarget}</span>
        <span className="text-[10px] text-white/20 flex-1 min-w-0 text-right">{formatDate(record.completedAt)}</span>
        <span className="text-[10px] text-white/20 flex-shrink-0 ml-2 font-mono">{formatDuration(record.durationMs)}</span>
        {expanded ? <ChevronUp size={12} className="text-white/20" /> : <ChevronDown size={12} className="text-white/20" />}
      </button>

      <AnimatePresence>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden"
          >
            <div className="px-3.5 pb-3 border-t border-white/[0.04] text-[11px] text-white/30 space-y-2 pt-2">
              {record.buildError && (
                <div className="flex gap-2 bg-red-500/6 border border-red-500/10 rounded-lg p-2">
                  <XCircle size={10} className="text-red-400 mt-0.5 flex-shrink-0" />
                  <span className="text-red-300/80 text-[10px] break-all">{record.buildError}</span>
                </div>
              )}
              {record.artifactUrl && (
                <a href={record.artifactUrl} target="_blank" rel="noopener noreferrer"
                   className="text-[#6C63FF] hover:underline truncate block text-[10px]">
                  Download artifact
                </a>
              )}
              {record.enabledModules?.length > 0 && (
                <div className="flex flex-wrap gap-1 pt-1">
                  {record.enabledModules.map(m => (
                    <span key={m} className="bg-white/[0.03] border border-white/[0.06] rounded-md px-1.5 py-0.5 text-[10px] text-white/35">
                      {m}
                    </span>
                  ))}
                </div>
              )}
              {onRebuild && (
                <div className="pt-2 mt-1 border-t border-white/[0.04] flex justify-end">
                  <button
                    type="button"
                    onClick={() => onRebuild(record)}
                    className="btn-ghost !py-1.5 !px-3 !text-[10px] flex items-center gap-1.5"
                  >
                    <RefreshCw size={10} /> Rebuild
                  </button>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ─── Main Component ──────────────────────────────────

export default function BuildDashboard({ projectId, projectName, onRebuild }: BuildDashboardProps) {
  const [history, setHistory] = useState<BuildRecord[]>([]);
  const [metrics, setMetrics] = useState<BuildMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [view, setView] = useState<'timeline' | 'list'>('timeline');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [hist, met] = await Promise.all([
        conversionApi.buildHistory(projectId, 10),
        conversionApi.buildMetrics(projectId),
      ]);
      setHistory(hist);
      setMetrics(met);
    } catch (e: any) {
      setError(e?.response?.data?.message || 'Failed to load build history');
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => { load(); }, [load]);

  return (
    <motion.div
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2 }}
      className="space-y-4 pt-4"
    >
      {/* Header with view toggle */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <GitBranch size={13} className="text-[#6C63FF]" />
          <span className="text-[12px] font-semibold">Build History</span>
          <span className="text-[10px] text-white/20">· {projectName}</span>
        </div>
        <div className="flex items-center gap-1">
          {/* View toggle */}
          <div className="flex rounded-lg border border-white/[0.06] overflow-hidden mr-1">
            <button
              type="button"
              onClick={() => setView('timeline')}
              className={`p-1.5 transition-colors ${view === 'timeline' ? 'bg-white/[0.08] text-white/60' : 'text-white/20 hover:text-white/40'}`}
              title="Timeline view"
            >
              <GitCommit size={12} />
            </button>
            <button
              type="button"
              onClick={() => setView('list')}
              className={`p-1.5 transition-colors ${view === 'list' ? 'bg-white/[0.08] text-white/60' : 'text-white/20 hover:text-white/40'}`}
              title="List view"
            >
              <List size={12} />
            </button>
          </div>
          <button
            type="button"
            onClick={load}
            disabled={loading}
            className="p-1.5 rounded-lg hover:bg-white/[0.04] text-white/20 hover:text-white/50 transition-colors"
          >
            <RefreshCw size={12} className={loading ? 'animate-spin' : ''} />
          </button>
        </div>
      </div>

      {/* Metrics row */}
      {metrics && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          <StatCard label="Total" value={metrics.totalBuilds} />
          <StatCard label="Passed" value={metrics.successfulBuilds} accent="text-[#00C896]" />
          <StatCard label="Failed" value={metrics.failedBuilds} accent="text-red-400" />
          <StatCard
            label="Rate"
            value={`${metrics.successRate}%`}
            accent={metrics.successRate >= 80 ? 'text-[#00C896]' : metrics.successRate >= 50 ? 'text-accent-orange' : 'text-red-400'}
          />
        </div>
      )}

      {/* Content */}
      {loading ? (
        <div className="space-y-2">
          {[0, 1, 2].map(i => (
            <div key={i} className="h-10 rounded-xl bg-white/[0.02] animate-pulse" />
          ))}
        </div>
      ) : error ? (
        <p className="text-[11px] text-red-400">{error}</p>
      ) : history.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-8 text-white/15">
          <Package size={24} />
          <p className="text-[12px]">No builds yet for this project</p>
        </div>
      ) : view === 'timeline' ? (
        <VersionTimeline records={history} onRebuild={onRebuild} />
      ) : (
        <div className="space-y-1.5">
          {history.map(record => (
            <BuildRow key={record.id} record={record} onRebuild={onRebuild} />
          ))}
        </div>
      )}
    </motion.div>
  );
}
