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
  expiresIn: number;       // seconds — matches backend LoginResponse.expiresIn / RefreshResponse.expiresIn
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
  enabledModules?: string[];
  targetPlatform?: string;
  moduleConfig?: ModuleConfig;
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

export interface DomainLockConfig {
  allowedDomains?: string[];
  blockedDomains?: string[];
  blockMessage?: string;
  allowExternalInBrowser?: boolean;
}

export interface TitleBarConfig {
  text?: string;
  overlayColor?: string;
  symbolColor?: string;
  overlayHeight?: number;
}

export interface WatermarkConfig {
  text?: string;
  position?: string;
  showDaysRemaining?: boolean;
  badgeColor?: string;
  textColor?: string;
  opacity?: number;
}

export interface ExpiryConfig {
  expiresAt?: string;
  lockMessage?: string;
  upgradeUrl?: string;
}

export interface ModuleConfig {
  domainLock?: DomainLockConfig;
  titleBar?: TitleBarConfig;
  watermark?: WatermarkConfig;
  expiry?: ExpiryConfig;
}

export interface CreateConversionRequest {
  projectName: string;
  websiteUrl: string;
  appTitle: string;
  iconFile?: string;
  enabledModules?: string[];
  targetPlatform?: string;
  moduleConfig?: ModuleConfig;
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
