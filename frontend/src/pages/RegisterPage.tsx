import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { User, Mail, Lock, Phone, AlertCircle, CheckCircle2, Loader2 } from 'lucide-react';
import { FcGoogle } from 'react-icons/fc';
import { useAuth } from '../hooks/useAuth';

export default function RegisterPage() {
  const [form, setForm] = useState({ username: '', email: '', password: '', phoneNumber: '' });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const { register, loginWithGoogle } = useAuth();
  const navigate = useNavigate();

  const update = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await register(form.username, form.email, form.password, Number(form.phoneNumber));
      setSuccess(true);
      setTimeout(() => navigate('/login'), 2000);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center px-6 pt-20">
      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 80, damping: 20 }}
        className="w-full max-w-md"
      >
        <div className="glass-card p-8 sm:p-10">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-bold tracking-tight mb-2">Create account</h1>
            <p className="text-sm text-white/40">Start converting websites in minutes</p>
          </div>

          {error && (
            <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}
              className="flex items-center gap-2 p-3 rounded-xl bg-red-500/10 border border-red-500/20 mb-6">
              <AlertCircle size={16} className="text-red-400 flex-shrink-0" />
              <p className="text-sm text-red-300">{error}</p>
            </motion.div>
          )}

          {success && (
            <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}
              className="flex items-center gap-2 p-3 rounded-xl bg-green-500/10 border border-green-500/20 mb-6">
              <CheckCircle2 size={16} className="text-green-400 flex-shrink-0" />
              <p className="text-sm text-green-300">Account created! Redirecting to login...</p>
            </motion.div>
          )}

          <button
            onClick={async () => {
              setError('');
              setGoogleLoading(true);
              try {
                await loginWithGoogle();
                navigate('/dashboard');
              } catch (err: any) {
                setError(err.message || 'Google sign-in failed.');
              } finally {
                setGoogleLoading(false);
              }
            }}
            disabled={loading || googleLoading || success}
            className="glass-card w-full flex items-center justify-center gap-3 py-2 px-4 rounded-lg hover:opacity-90 transition mb-4"
          >
            {googleLoading ? (
              <Loader2 className="animate-spin w-5 h-5 text-white/70" />
            ) : (
              <>
                <FcGoogle className="w-5 h-5" />
                <span className="text-sm">Continue with Google</span>
              </>
            )}
          </button>

          <div className="flex items-center gap-2 mb-6">
            <hr className="flex-1 border-white/10" />
            <span className="text-xs text-white/30 uppercase tracking-widest">or email</span>
            <hr className="flex-1 border-white/10" />
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {[
              { icon: User, label: 'Username', field: 'username', type: 'text', placeholder: 'johndoe' },
              { icon: Mail, label: 'Email', field: 'email', type: 'email', placeholder: 'you@example.com' },
              { icon: Lock, label: 'Password', field: 'password', type: 'password', placeholder: '••••••••' },
              { icon: Phone, label: 'Phone Number', field: 'phoneNumber', type: 'tel', placeholder: '9876543210' },
            ].map(({ icon: Icon, label, field, type, placeholder }) => (
              <div key={field}>
                <label className="text-xs text-white/40 mb-1.5 block">{label}</label>
                <div className="relative">
                  <Icon size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-white/20" />
                  <input
                    type={type}
                    value={(form as any)[field]}
                    onChange={update(field)}
                    placeholder={placeholder}
                    className="input-field pl-10"
                    required
                  />
                </div>
              </div>
            ))}

            <button type="submit" disabled={loading || success} className="btn-accent w-full mt-2 flex items-center justify-center gap-2">
              {loading ? <Loader2 size={16} className="animate-spin" /> : null}
              {loading ? 'Creating...' : 'Create Account'}
            </button>
          </form>

          <p className="text-center text-sm text-white/30 mt-6">
            Already have an account?{' '}
            <Link to="/login" className="text-accent-blue hover:underline">Sign in</Link>
          </p>
        </div>
      </motion.div>
    </div>
  );
}
