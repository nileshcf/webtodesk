import axios from 'axios';
import type {
  LoginRequest, SignupRequest, AuthTokens, ConversionProject,
  CreateConversionRequest, ElectronConfig
} from '../types';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

// ─── Token Management ───────────────────────────────
let refreshTimer: ReturnType<typeof setTimeout> | null = null;

export function getAccessToken(): string | null {
  return localStorage.getItem('accessToken');
}

export function getRefreshToken(): string | null {
  return localStorage.getItem('refreshToken');
}

function saveTokens(tokens: AuthTokens) {
  localStorage.setItem('accessToken', tokens.accessToken);
  localStorage.setItem('refreshToken', tokens.refreshToken);
  scheduleRefresh(tokens.expiresIn);
}

function clearTokens() {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  if (refreshTimer) clearTimeout(refreshTimer);
}

function scheduleRefresh(expiresInSeconds: number) {
  if (refreshTimer) clearTimeout(refreshTimer);
  // Refresh 60 seconds before expiry
  const delay = Math.max((expiresInSeconds - 60) * 1000, 10000);
  refreshTimer = setTimeout(async () => {
    try {
      await authApi.refresh();
    } catch {
      clearTokens();
      window.location.href = '/login';
    }
  }, delay);
}

// Attach access token to every request
api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ─── Auth API ───────────────────────────────────────
export const authApi = {
  async register(data: SignupRequest) {
    const res = await api.post('/user/auth/register', data);
    return res.data;
  },

  async login(data: LoginRequest): Promise<AuthTokens> {
    const res = await api.post<AuthTokens>('/user/auth/login', data);
    saveTokens(res.data);
    return res.data;
  },

  async refresh(): Promise<void> {
    const refreshToken = getRefreshToken();
    if (!refreshToken) throw new Error('No refresh token');
    const res = await api.post('/user/auth/refresh', { refreshToken });
    const newAccessToken = res.data.accessToken;
    const expiresIn = res.data.expiresIn;
    localStorage.setItem('accessToken', newAccessToken);
    scheduleRefresh(expiresIn);
  },

  async logout(): Promise<void> {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      try {
        await api.post('/user/auth/logout', { refreshToken });
      } catch {
        // continue logout even if backend fails
      }
    }
    clearTokens();
  },

  async getProfile() {
    const res = await api.get('/user/me');
    return res.data;
  },

  initFromStorage() {
    const token = getAccessToken();
    if (token) {
      // Re-schedule refresh (assume ~10 min left since we don't store exact expiry)
      scheduleRefresh(600);
    }
  },
};

// ─── Conversion API ─────────────────────────────────
export const conversionApi = {
  async list(): Promise<ConversionProject[]> {
    const res = await api.get<ConversionProject[]>('/conversion/conversions');
    return res.data;
  },

  async create(data: CreateConversionRequest): Promise<ConversionProject> {
    const res = await api.post<ConversionProject>('/conversion/conversions', data);
    return res.data;
  },

  async getById(id: string): Promise<ConversionProject> {
    const res = await api.get<ConversionProject>(`/conversion/conversions/${id}`);
    return res.data;
  },

  async update(id: string, data: Partial<CreateConversionRequest>): Promise<ConversionProject> {
    const res = await api.put<ConversionProject>(`/conversion/conversions/${id}`, data);
    return res.data;
  },

  async remove(id: string): Promise<void> {
    await api.delete(`/conversion/conversions/${id}`);
  },

  async generate(id: string): Promise<ElectronConfig> {
    const res = await api.post<ElectronConfig>(`/conversion/conversions/${id}/generate`);
    return res.data;
  },
};
