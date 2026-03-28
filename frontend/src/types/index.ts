export interface User {
  id: string;
  email: string;
  username: string;
  roles: string[];
}

export interface AuthTokens {
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  tokenExpiryInSeconds: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  username: string;
  email: string;
  password: string;
  phoneNumber: number;
}

export interface ConversionProject {
  id: string;
  projectName: string;
  websiteUrl: string;
  appTitle: string;
  iconFile: string;
  currentVersion: string;
  status: 'DRAFT' | 'READY' | 'BUILDING' | 'FAILED';
  createdBy?: string;
  buildError?: string | null;
  downloadAvailable: boolean;
  downloadUrl?: string | null;
  buildProgress?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BuildStatusResponse {
  projectId: string;
  projectName: string;
  status: 'DRAFT' | 'READY' | 'BUILDING' | 'FAILED';
  buildError: string | null;
  downloadAvailable: boolean;
  downloadUrl: string | null;
  updatedAt: string | null;
}

export interface CreateConversionRequest {
  projectName: string;
  websiteUrl: string;
  appTitle: string;
  iconFile?: string;
}

export interface ElectronConfig {
  projectName: string;
  appTitle: string;
  websiteUrl: string;
  files: Record<string, string>;
}

export interface UserProfileDetails {
  userId: string;
  email: string;
  username: string;
  name: string | null;
  phoneNumber: number | null;
  avatarUrl: string | null;
  roles: string[];
  emailVerified: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateProfileRequest {
  username: string | null;
  name: string | null;
  phoneNumber: number | null;
  avatarUrl: string | null;
}
