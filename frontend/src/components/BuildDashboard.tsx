import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import {
  CheckCircle2, XCircle, Clock, Cpu, Package, ChevronDown, ChevronUp,
  BarChart3, Layers, RefreshCw
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

// ─── Stat Card ───────────────────────────────────────

function StatCard({ label, value, sub, accent }: {
  label: string; value: string | number; sub?: string; accent?: string;
}) {
  return (
    <div className="relative flex flex-col gap-1 rounded-xl border border-white/[0.06] bg-white/[0.02] backdrop-blur-md px-4 py-3 overflow-hidden group hover:border-white/10 transition-colors">
      <div className="absolute inset-0 bg-gradient-to-br from-white/[0.02] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
      <span className="text-[10px] font-semibold text-white/40 uppercase tracking-widest relative z-10">{label}</span>
      <span className={`text-2xl font-bold tracking-tight relative z-10 ${accent ?? 'text-white'}`}>{value}</span>
      {sub && <span className="text-[10px] font-medium text-white/30 relative z-10">{sub}</span>}
    </div>
  );
}

// ─── Build Row ───────────────────────────────────────

function BuildRow({ record, onRebuild }: { record: BuildRecord; onRebuild?: (record: BuildRecord) => void; }) {
  const [expanded, setExpanded] = useState(false);
  const success = record.result === 'READY';

  return (
    <div className="border border-white/[0.06] rounded-xl overflow-hidden">
      <button
        type="button"
        onClick={() => setExpanded(e => !e)}
        className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-white/[0.02] transition-colors"
      >
        {success
          ? <CheckCircle2 size={15} className="text-accent-green flex-shrink-0" />
          : <XCircle size={15} className="text-red-400 flex-shrink-0" />}
        <span className={`text-xs font-semibold flex-shrink-0 w-14 ${success ? 'text-accent-green' : 'text-red-400'}`}>
          {success ? 'SUCCESS' : 'FAILED'}
        </span>
        <span className="text-xs text-white/50 flex-shrink-0">{record.buildTarget.toUpperCase()}</span>
        <span className="text-xs text-white/30 flex-1 min-w-0 text-right">{formatDate(record.completedAt)}</span>
        <span className="text-xs text-white/30 flex-shrink-0 ml-3">{formatDuration(record.durationMs)}</span>
        {expanded ? <ChevronUp size={13} className="text-white/30" /> : <ChevronDown size={13} className="text-white/30" />}
      </button>

      {expanded && (
        <div className="px-4 pb-3 border-t border-white/[0.04] text-xs text-white/40 space-y-2 pt-2">
          {record.buildError && (
            <div className="flex gap-2 bg-red-500/10 border border-red-500/20 rounded-lg p-2">
              <XCircle size={12} className="text-red-400 mt-0.5 flex-shrink-0" />
              <span className="text-red-300 break-all">{record.buildError}</span>
            </div>
          )}
          {record.artifactUrl && (
            <a
              href={record.artifactUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-accent-blue hover:underline truncate block"
            >
              Download artifact
            </a>
          )}
          {record.enabledModules?.length > 0 && (
            <div className="flex flex-wrap gap-1 pt-1">
              {record.enabledModules.map(m => (
                <span key={m} className="bg-white/[0.05] border border-white/[0.08] rounded-full px-2 py-0.5 text-white/50">
                  {m}
                </span>
              ))}
            </div>
          )}

          {onRebuild && (
            <div className="pt-4 mt-2 border-t border-white/[0.04] flex justify-end">
              <button
                type="button"
                onClick={() => onRebuild(record)}
                className="btn-accent !py-1.5 !px-4 !text-xs flex items-center gap-1.5"
              >
                <Cpu size={13} /> Edit Config & Rebuild
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ─── Main Component ──────────────────────────────────

export default function BuildDashboard({ projectId, projectName, onRebuild }: BuildDashboardProps) {
  const [history, setHistory] = useState<BuildRecord[]>([]);
  const [metrics, setMetrics] = useState<BuildMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

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
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-4"
    >
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <BarChart3 size={16} className="text-accent-blue" />
          <span className="text-sm font-semibold">Build History</span>
          <span className="text-xs text-white/30">· {projectName}</span>
        </div>
        <button
          type="button"
          onClick={load}
          disabled={loading}
          className="p-1.5 rounded-lg hover:bg-white/[0.04] text-white/30 hover:text-white/60 transition-colors"
        >
          <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
        </button>
      </div>

      {/* Metrics row */}
      {metrics && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          <StatCard label="Total" value={metrics.totalBuilds} />
          <StatCard label="Success" value={metrics.successfulBuilds} accent="text-accent-green" />
          <StatCard label="Failed" value={metrics.failedBuilds} accent="text-red-400" />
          <StatCard
            label="Success Rate"
            value={`${metrics.successRate}%`}
            sub={`avg ${formatDuration(metrics.avgDurationMs)}`}
            accent={metrics.successRate >= 80 ? 'text-accent-green' : metrics.successRate >= 50 ? 'text-accent-orange' : 'text-red-400'}
          />
        </div>
      )}

      {/* History */}
      {loading ? (
        <div className="space-y-2">
          {[0, 1, 2].map(i => (
            <div key={i} className="h-11 rounded-xl bg-white/[0.03] animate-pulse" />
          ))}
        </div>
      ) : error ? (
        <p className="text-xs text-red-400">{error}</p>
      ) : history.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-8 text-white/20">
          <Package size={28} />
          <p className="text-sm">No builds yet for this project</p>
        </div>
      ) : (
        <div className="space-y-2">
          {history.map(record => (
            <BuildRow key={record.id} record={record} onRebuild={onRebuild} />
          ))}
        </div>
      )}
    </motion.div>
  );
}
