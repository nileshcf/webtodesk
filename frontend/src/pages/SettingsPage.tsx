import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { User, Phone, Image, AlertCircle, CheckCircle2, Loader2 } from 'lucide-react';
import { authApi } from '../services/api';
import { useAuth } from '../hooks/useAuth';
import type { UpdateProfileRequest, UserProfileDetails } from '../types';

export default function SettingsPage() {
  const { isAuthenticated, updateProfile } = useAuth();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const [form, setForm] = useState({
    username: '',
    name: '',
    phoneNumber: '',
    avatarUrl: '',
  });

  const hydrate = async () => {
    setLoading(true);
    setError('');
    setSuccess(false);
    try {
      const profile: UserProfileDetails = await authApi.getProfileDetails();
      setForm({
        username: profile.username ?? '',
        name: profile.name ?? '',
        phoneNumber: profile.phoneNumber != null ? String(profile.phoneNumber) : '',
        avatarUrl: profile.avatarUrl ?? '',
      });
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load profile.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isAuthenticated) hydrate();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  const handleChange =
    (field: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => {
      setForm((f) => ({ ...f, [field]: e.target.value }));
    };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    setSuccess(false);

    try {
      const payload: UpdateProfileRequest = {
        username: form.username.trim() ? form.username.trim() : null,
        name: form.name.trim() ? form.name.trim() : null,
        phoneNumber: form.phoneNumber.trim() ? Number(form.phoneNumber) : null,
        avatarUrl: form.avatarUrl.trim() ? form.avatarUrl.trim() : null,
      };

      await updateProfile(payload);

      // Keep the form aligned with backend truth.
      await hydrate();
      setSuccess(true);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to update profile.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center px-6 pt-24 pb-16">
      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 80, damping: 20 }}
        className="w-full max-w-md"
      >
        <div className="glass-card p-8 sm:p-10">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-bold tracking-tight mb-2">Settings</h1>
            <p className="text-sm text-white/40">Update your profile details</p>
          </div>

          {error && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="flex items-center gap-2 p-3 rounded-xl bg-red-500/10 border border-red-500/20 mb-6"
            >
              <AlertCircle size={16} className="text-red-400 flex-shrink-0" />
              <p className="text-sm text-red-300">{error}</p>
            </motion.div>
          )}

          {success && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="flex items-center gap-2 p-3 rounded-xl bg-green-500/10 border border-green-500/20 mb-6"
            >
              <CheckCircle2 size={16} className="text-green-400 flex-shrink-0" />
              <p className="text-sm text-green-300">Profile updated.</p>
            </motion.div>
          )}

          {loading ? (
            <div className="flex items-center justify-center py-10">
              <Loader2 size={24} className="animate-spin text-white/30" />
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="text-xs text-white/40 mb-1.5 block">Username</label>
                <div className="relative">
                  <User size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-white/20" />
                  <input
                    type="text"
                    value={form.username}
                    onChange={handleChange('username')}
                    placeholder="johndoe"
                    className="input-field pl-10"
                    required
                  />
                </div>
              </div>

              <div>
                <label className="text-xs text-white/40 mb-1.5 block">Name</label>
                <div className="relative">
                  <User size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-white/20" />
                  <input
                    type="text"
                    value={form.name}
                    onChange={handleChange('name')}
                    placeholder="Your name"
                    className="input-field pl-10"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs text-white/40 mb-1.5 block">Phone Number</label>
                <div className="relative">
                  <Phone size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-white/20" />
                  <input
                    type="tel"
                    value={form.phoneNumber}
                    onChange={handleChange('phoneNumber')}
                    placeholder="9876543210"
                    className="input-field pl-10"
                    inputMode="numeric"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs text-white/40 mb-1.5 block">Avatar URL</label>
                <div className="relative">
                  <Image size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-white/20" />
                  <input
                    type="url"
                    value={form.avatarUrl}
                    onChange={handleChange('avatarUrl')}
                    placeholder="https://example.com/avatar.png"
                    className="input-field pl-10"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={saving}
                className="btn-accent w-full mt-2 flex items-center justify-center gap-2"
              >
                {saving ? <Loader2 size={16} className="animate-spin" /> : null}
                {saving ? 'Saving...' : 'Save Changes'}
              </button>
            </form>
          )}
        </div>
      </motion.div>
    </div>
  );
}

