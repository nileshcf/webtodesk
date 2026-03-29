import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Mail, Lock, AlertCircle, Loader2 } from 'lucide-react';
import { FcGoogle } from 'react-icons/fc';
import { useAuth } from '../hooks/useAuth';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const { login, loginWithGoogle } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(email, password);
      navigate('/dashboard');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Invalid credentials. Please try again.');
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
            <h1 className="text-3xl font-bold tracking-tight mb-2">Welcome back</h1>
            <p className="text-sm text-white/40">Sign in to your WebToDesk account</p>
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
            disabled={loading || googleLoading}
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
            <div>
              <label className="text-xs text-white/40 mb-1.5 block">Email</label>
              <div className="relative">
                <Mail size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-white/20" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  className="input-field pl-10"
                  required
                />
              </div>
            </div>

            <div>
              <label className="text-xs text-white/40 mb-1.5 block">Password</label>
              <div className="relative">
                <Lock size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-white/20" />
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="input-field pl-10"
                  required
                />
              </div>
            </div>

            <button type="submit" disabled={loading} className="btn-accent w-full mt-2 flex items-center justify-center gap-2">
              {loading ? <Loader2 size={16} className="animate-spin" /> : null}
              {loading ? 'Signing in...' : 'Sign In'}
            </button>
          </form>

          <p className="text-center text-sm text-white/30 mt-6">
            Don't have an account?{' '}
            <Link to="/register" className="text-accent-blue hover:underline">Create one</Link>
          </p>
        </div>
      </motion.div>
    </div>
  );
}
