// Module configuration types for WebToDesk frontend

export interface FeatureConfig {
  tier: LicenseTier;
  buildFlags: BuildFlags;
  modules: ModuleConfig;
}

// Free Tier Modules
export interface SplashScreenConfig {
  logoUrl?: string;
  showOurLogo: boolean;
  durationMs: number;
}

export interface TitleBarConfig {
  enabled: boolean;
  text?: string;
  style?: 'default' | 'hidden' | 'hiddenInset';
}

export interface DomainLockConfig {
  allowedDomains: string[];
  blockedDomains: string[];
  blockMessage: string;
}

export interface WatermarkConfig {
  text?: string;
  imageUrl?: string;
  position: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right' | 'center';
  opacity: number;
  color?: string;
  fontSize?: number;
  useOurBranding: boolean;
}

export interface ExpiryConfig {
  expiresAt: string;
  lockMessage: string;
  upgradeUrl: string;
}

// Pro Tier Modules
export interface KeyBindingConfig {
  accelerator: string;
  action: 'reload' | 'goBack' | 'goForward' | 'zoomIn' | 'zoomOut' | 'toggleDevTools' | 'ipc';
  ipcChannel?: string;
}

export interface OfflineCacheConfig {
  strategy: 'cache-first' | 'network-first' | 'stale-while-revalidate';
  maxAgeSeconds: number;
  maxSizeMb: number;
}

export interface AutoUpdateConfig {
  feedUrl: string;
  silent: boolean;
  autoInstall: boolean;
}

export interface TrayConfig {
  tooltip: string;
  contextMenu: TrayMenuItem[];
}

export interface TrayMenuItem {
  label: string;
  action: 'show' | 'hide' | 'reload' | 'quit' | 'separator';
  ipcChannel?: string;
}

export interface ClipboardConfig {
  allowRead: boolean;
  allowWrite: boolean;
}

export interface WindowPolishConfig {
  blur: boolean;
  alwaysOnTop: boolean;
  frame: boolean;
  opacity: number;
  vibrancy?: string;
}

export interface RightClickConfig {
  disable: boolean;
  customMenuItems: RightClickMenuItem[];
}

export interface RightClickMenuItem {
  label: string;
  action: string;
  ipcChannel?: string;
}

export interface FileSystemConfig {
  allowedPaths: string[];
  mode: 'read' | 'read-write';
}

export interface GlobalHotkeyConfig {
  accelerator: string;
  action: string;
  ipcChannel?: string;
}

// Combined Module Configuration
export interface ModuleConfig {
  // Free Tier Modules
  splashScreen?: SplashScreenConfig;
  titleBar?: TitleBarConfig;
  domainLock?: DomainLockConfig;
  fileDownload?: boolean;
  watermark?: WatermarkConfig;
  expiry?: ExpiryConfig;

  // Pro Tier Modules
  screenCaptureProtection?: boolean;
  keyBindings?: KeyBindingConfig[];
  offlineCache?: OfflineCacheConfig;
  autoUpdate?: AutoUpdateConfig;
  nativeNotifications?: boolean;
  systemTray?: TrayConfig;
  darkLightSync?: boolean;
  clipboard?: ClipboardConfig;
  windowPolish?: WindowPolishConfig;
  rightClick?: RightClickConfig;
  fileSystem?: FileSystemConfig;
  globalHotkeys?: GlobalHotkeyConfig[];
}

export interface ModuleRegistry {
  enabled: string[];
  disabled: string[];
  config: Record<string, any>;
}

export interface ModuleAvailability {
  moduleId: string;
  name: string;
  description: string;
  tier: LicenseTier;
  enabled: boolean;
  configurable: boolean;
  config?: ModuleConfig;
}

// Import types from other files
import { LicenseTier } from './license';
import { BuildFlags } from './build';
