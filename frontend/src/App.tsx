import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './hooks/useAuth';
import Navbar from './components/layout/Navbar';
import Footer from './components/layout/Footer';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import SettingsPage from './pages/SettingsPage';
import ModuleLabPage from './pages/ModuleLabPage';

function AppShellLoading() {
  return (
    <div className="min-h-screen pt-24 pb-16 px-6">
      <div className="max-w-5xl mx-auto">
        <div className="glass-card p-6 sm:p-8">
          <div className="h-6 w-56 rounded-lg bg-white/5 mb-3 animate-pulse" />
          <div className="h-4 w-80 rounded-lg bg-white/5 mb-8 animate-pulse" />
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="h-24 rounded-2xl bg-white/5 border border-white/5 animate-pulse" />
            <div className="h-24 rounded-2xl bg-white/5 border border-white/5 animate-pulse" />
            <div className="h-24 rounded-2xl bg-white/5 border border-white/5 animate-pulse" />
          </div>
        </div>
      </div>
    </div>
  );
}

/** Blocks unauthenticated access → redirects to /login */
function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return <AppShellLoading />;
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
}

/** Blocks authenticated access → redirects to /dashboard */
function GuestRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return <AppShellLoading />;
  return isAuthenticated ? <Navigate to="/dashboard" replace /> : <>{children}</>;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/module-lab" element={<ModuleLabPage />} />
      <Route path="/login" element={<GuestRoute><LoginPage /></GuestRoute>} />
      <Route path="/register" element={<GuestRoute><RegisterPage /></GuestRoute>} />
      <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
      <Route path="/settings" element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div className="flex flex-col min-h-screen">
          <Navbar />
          <main className="flex-1">
            <AppRoutes />
          </main>
          <Footer />
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}
