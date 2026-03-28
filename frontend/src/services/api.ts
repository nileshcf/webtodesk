import axios from 'axios';
import type {
  LoginRequest, SignupRequest, AuthTokens, ConversionProject,
  CreateConversionRequest, ElectronConfig, BuildStatusResponse,
  User, UserProfileDetails, UpdateProfileRequest
} from '../types';

const api = axios.create({
  baseURL: '/',
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
  if (tokens.accessToken) {
    localStorage.setItem('accessToken', tokens.accessToken);
  }
  if (tokens.refreshToken) {
    localStorage.setItem('refreshToken', tokens.refreshToken);
  }
  // Schedule refresh (fallback to 15 mins if not provided)
  scheduleRefresh(tokens.expiresIn || 900);
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

// Avoid infinite refresh loops
let isRefreshing = false;
let failedQueue: Array<{ resolve: (token: string | null) => void, reject: (err: any) => void }> = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

// Handle 401s and automatically refresh Token
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Check if 401 Unauthorized and not already retried
    if (
      error.response?.status === 401 && 
      !originalRequest._retry && 
      originalRequest.url !== '/user/auth/login' && 
      originalRequest.url !== '/user/auth/refresh'
    ) {
      if (isRefreshing) {
        return new Promise<string | null>(function(resolve, reject) {
          failedQueue.push({ resolve, reject });
        }).then(token => {
          originalRequest.headers['Authorization'] = 'Bearer ' + token;
          return api(originalRequest);
        }).catch(err => {
          return Promise.reject(err);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        await authApi.refresh();
        const newToken = getAccessToken();
        processQueue(null, newToken);
        originalRequest.headers['Authorization'] = 'Bearer ' + newToken;
        return api(originalRequest);
      } catch (err) {
        processQueue(err, null);
        clearTokens();
        // Optionially redirect to login or let the component handle it
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

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
    const res = await api.post<AuthTokens>('/user/auth/refresh', { refreshToken });
    saveTokens(res.data);
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

  async getProfileDetails(): Promise<UserProfileDetails> {
    const res = await api.get<UserProfileDetails>('/user/me');
    return res.data;
  },

  async updateProfile(data: UpdateProfileRequest): Promise<UserProfileDetails> {
    const res = await api.put<UserProfileDetails>('/user/me', data);
    return res.data;
  },

  async getProfile() {
    const profile = await authApi.getProfileDetails();
    return {
      id: profile.userId,
      email: profile.email,
      username: profile.username,
      roles: profile.roles,
    } satisfies User;
  },

  initFromStorage() {
    const token = getAccessToken();
    if (token) {
      // Decode JWT to find actual remaining time instead of guessing
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const expiresAt = payload.exp * 1000; // JWT exp is in seconds
        const remainingMs = expiresAt - Date.now();
        if (remainingMs <= 0) {
          // Token already expired — try refreshing immediately
          authApi.refresh().catch(() => {
            clearTokens();
            window.location.href = '/login';
          });
        } else {
          scheduleRefresh(Math.floor(remainingMs / 1000));
        }
      } catch {
        // Fallback: assume ~10 min left if JWT can't be decoded
        scheduleRefresh(600);
      }
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

  async build(id: string): Promise<BuildStatusResponse> {
    const res = await api.post<BuildStatusResponse>(`/conversion/conversions/${id}/build`);
    return res.data;
  },

  async buildStatus(id: string): Promise<BuildStatusResponse> {
    const res = await api.get<BuildStatusResponse>(`/conversion/conversions/${id}/build/status`);
    return res.data;
  },

  getDownloadUrl(id: string): string {
    // Returns the gateway-proxied download endpoint which 302-redirects to R2 public URL
    return `/conversion/conversions/${id}/build/download`;
  },

  subscribeToBuildProgress(id: string): EventSource {
    return new EventSource(`/conversion/conversions/${id}/build/stream`);
  },

  async buildHistory(projectId: string, limit = 10) {
    const res = await api.get(`/conversion/build/history/${projectId}?limit=${limit}`);
    return res.data as Array<{
      id: string; projectId: string; projectName: string; userEmail: string;
      tier: string; result: string; buildError?: string; artifactUrl?: string;
      buildTarget: string; enabledModules: string[]; startedAt: string;
      completedAt: string; durationMs: number;
    }>;
  },

  async buildMetrics(projectId: string) {
    const res = await api.get(`/conversion/build/metrics/${projectId}`);
    return res.data as {
      totalBuilds: number; successfulBuilds: number; failedBuilds: number;
      avgDurationMs: number; successRate: number;
    };
  },

  async queueStatus() {
    const res = await api.get('/conversion/build/queue/status');
    return res.data as {
      normalQueueLength: number; priorityQueueLength: number;
      averageWaitTime: number; estimatedPosition: number;
    };
  },

  async userBuildHistory(limit = 10) {
    const res = await api.get(`/conversion/build/metrics?period=month`);
    return res.data as { recentBuilds: any[]; queueStats: any };
  },
};
