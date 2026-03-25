import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Plus, Globe, Monitor, Trash2, Download, Loader2,
  ExternalLink, ChevronDown, X, AlertCircle, CheckCircle2
} from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { conversionApi } from '../services/api';
import type { ConversionProject, ElectronConfig } from '../types';

export default function DashboardPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<ConversionProject[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [generatedConfig, setGeneratedConfig] = useState<ElectronConfig | null>(null);
  const [form, setForm] = useState({ projectName: '', websiteUrl: '', appTitle: '', iconFile: '' });
  const [formError, setFormError] = useState('');
  const [formLoading, setFormLoading] = useState(false);

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

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    setFormLoading(true);
    try {
      await conversionApi.create({
        projectName: form.projectName,
        websiteUrl: form.websiteUrl,
        appTitle: form.appTitle,
        iconFile: form.iconFile || undefined,
      });
      setForm({ projectName: '', websiteUrl: '', appTitle: '', iconFile: '' });
      setShowForm(false);
      fetchProjects();
    } catch (err: any) {
      setFormError(err.response?.data?.message || 'Failed to create project');
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

  const handleDelete = async (id: string) => {
    try {
      await conversionApi.remove(id);
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
          <button onClick={() => setShowForm(!showForm)} className="btn-accent flex items-center gap-2">
            {showForm ? <X size={16} /> : <Plus size={16} />}
            {showForm ? 'Cancel' : 'New Conversion'}
          </button>
        </motion.div>

        {/* Create Form */}
        <AnimatePresence>
          {showForm && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="overflow-hidden mb-8"
            >
              <div className="glass-card p-6 sm:p-8">
                <h2 className="text-lg font-semibold mb-6 flex items-center gap-2">
                  <Globe size={18} className="text-accent-blue" /> Convert a Website
                </h2>

                {formError && (
                  <div className="flex items-center gap-2 p-3 rounded-xl bg-red-500/10 border border-red-500/20 mb-4">
                    <AlertCircle size={16} className="text-red-400" />
                    <p className="text-sm text-red-300">{formError}</p>
                  </div>
                )}

                <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="sm:col-span-2">
                    <label className="text-xs text-white/40 mb-1.5 block">Website URL</label>
                    <input
                      type="url"
                      value={form.websiteUrl}
                      onChange={(e) => setForm((f) => ({ ...f, websiteUrl: e.target.value }))}
                      placeholder="https://your-website.com"
                      className="input-field"
                      required
                    />
                  </div>
                  <div>
                    <label className="text-xs text-white/40 mb-1.5 block">App Title</label>
                    <input
                      type="text"
                      value={form.appTitle}
                      onChange={(e) => setForm((f) => ({ ...f, appTitle: e.target.value }))}
                      placeholder="My Desktop App"
                      className="input-field"
                      required
                    />
                  </div>
                  <div>
                    <label className="text-xs text-white/40 mb-1.5 block">Project Name</label>
                    <input
                      type="text"
                      value={form.projectName}
                      onChange={(e) => setForm((f) => ({ ...f, projectName: e.target.value }))}
                      placeholder="my-desktop-app"
                      className="input-field"
                      required
                    />
                  </div>
                  <div>
                    <label className="text-xs text-white/40 mb-1.5 block">Icon File (optional)</label>
                    <input
                      type="text"
                      value={form.iconFile}
                      onChange={(e) => setForm((f) => ({ ...f, iconFile: e.target.value }))}
                      placeholder="icon.ico"
                      className="input-field"
                    />
                  </div>
                  <div className="flex items-end">
                    <button type="submit" disabled={formLoading} className="btn-primary flex items-center gap-2 w-full justify-center">
                      {formLoading ? <Loader2 size={16} className="animate-spin" /> : <Monitor size={16} />}
                      {formLoading ? 'Creating...' : 'Create Conversion'}
                    </button>
                  </div>
                </form>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Projects List */}
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 size={24} className="animate-spin text-white/30" />
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
                    <button
                      onClick={() => handleGenerate(project.id)}
                      className="btn-ghost !py-2 !px-4 !text-xs flex items-center gap-1.5"
                    >
                      <Download size={13} /> Generate
                    </button>
                    <button
                      onClick={() => handleDelete(project.id)}
                      className="p-2 rounded-xl text-white/20 hover:text-red-400 hover:bg-red-500/10 transition-all"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
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
