import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { authApi, getAccessToken } from '../services/api';
import type { UpdateProfileRequest, User } from '../types';
import { signInWithPopup } from 'firebase/auth';
import { auth, googleProvider } from '../config/firebase';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string, phoneNumber: number) => Promise<void>;
  loginWithGoogle: () => Promise<void>;
  updateProfile: (data: UpdateProfileRequest) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const token = getAccessToken();
    if (token) {
      authApi.initFromStorage();
      authApi.getProfile()
        .then(setUser)
        .catch(() => setUser(null))
        .finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, []);

  const login = async (email: string, password: string) => {
    await authApi.login({ email, password });
    const profile = await authApi.getProfile();
    setUser(profile);
  };

  const register = async (username: string, email: string, password: string, phoneNumber: number) => {
    await authApi.register({ username, email, password, phoneNumber });
  };

  const loginWithGoogle = async () => {
    try {
      const result = await signInWithPopup(auth, googleProvider);
      const idToken = await result.user.getIdToken(true);
      await authApi.googleAuth({ idToken });
      const profile = await authApi.getProfile();
      setUser(profile);
    } catch (error: any) {
      if (error.code === 'auth/popup-closed-by-user') return;
      if (error.code === 'auth/account-exists-with-different-credential') {
        throw new Error('An account with this email exists. Please log in with your password.');
      }
      throw new Error('Google sign-in failed. Please try again.');
    }
  };

  const updateProfile = async (data: UpdateProfileRequest) => {
    await authApi.updateProfile(data);
    const profile = await authApi.getProfile();
    setUser(profile);
  };

  const logout = async () => {
    await authApi.logout();
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, register, loginWithGoogle, updateProfile, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
