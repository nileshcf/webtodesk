import axios from 'axios';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import type {
  BuildRequest,
  BuildResult,
  BuildStatusResponse,
  BuildProgress,
  CrossPlatformBuildRequest,
  CrossPlatformBuildResult,
  BuildMetrics,
  BuildConfigForm,
  TargetOS,
  FileType
} from '../types/build';
import { getAccessToken } from './api';

const BUILD_BASE = '/conversion/build';

export const buildApi = {
  // Trigger single platform build
  async triggerBuild(request: BuildRequest): Promise<BuildStatusResponse> {
    const res = await axios.post<BuildStatusResponse>(`${BUILD_BASE}/trigger`, request, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Trigger cross-platform build
  async triggerCrossPlatformBuild(request: CrossPlatformBuildRequest): Promise<CrossPlatformBuildResult> {
    const res = await axios.post<CrossPlatformBuildResult>(`${BUILD_BASE}/cross-platform`, request, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Get build status
  async getBuildStatus(projectId: string, targetOS?: TargetOS): Promise<BuildStatusResponse> {
    const url = targetOS 
      ? `${BUILD_BASE}/status/${projectId}?targetOS=${targetOS}`
      : `${BUILD_BASE}/status/${projectId}`;
    const res = await axios.get<BuildStatusResponse>(url, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Subscribe to build progress via SSE
  subscribeToBuildProgress(projectId: string, targetOS?: TargetOS): EventSource {
    const url = targetOS 
      ? `${BUILD_BASE}/progress/${projectId}?targetOS=${targetOS}`
      : `${BUILD_BASE}/progress/${projectId}`;
    
    return new EventSource(url);
  },

  // Subscribe to cross-platform build progress
  subscribeToCrossPlatformProgress(projectId: string): EventSource {
    return new EventSource(`${BUILD_BASE}/cross-platform/progress/${projectId}`);
  },

  // Get build metrics
  async getBuildMetrics(period: 'today' | 'week' | 'month' = 'month'): Promise<BuildMetrics> {
    const res = await axios.get<BuildMetrics>(`${BUILD_BASE}/metrics?period=${period}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Get queue status
  async getQueueStatus(): Promise<{
    normalQueueLength: number;
    priorityQueueLength: number;
    averageWaitTime: number;
    estimatedPosition?: number;
  }> {
    const res = await axios.get(`${BUILD_BASE}/queue/status`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Cancel build
  async cancelBuild(projectId: string, targetOS?: TargetOS): Promise<void> {
    const url = targetOS 
      ? `${BUILD_BASE}/cancel/${projectId}?targetOS=${targetOS}`
      : `${BUILD_BASE}/cancel/${projectId}`;
    await axios.post(url, {}, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
  },

  // Retry failed build
  async retryBuild(projectId: string, targetOS?: TargetOS): Promise<BuildStatusResponse> {
    const url = targetOS 
      ? `${BUILD_BASE}/retry/${projectId}?targetOS=${targetOS}`
      : `${BUILD_BASE}/retry/${projectId}`;
    const res = await axios.post<BuildStatusResponse>(url, {}, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Get available file types for OS
  async getAvailableFileTypes(targetOS: TargetOS): Promise<FileType[]> {
    const res = await axios.get<FileType[]>(`${BUILD_BASE}/file-types/${targetOS}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Validate build configuration
  async validateBuildConfig(config: BuildConfigForm): Promise<{
    valid: boolean;
    errors: string[];
    warnings: string[];
  }> {
    const res = await axios.post(`${BUILD_BASE}/validate-config`, config, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Get build history for project
  async getBuildHistory(projectId: string, limit: number = 10): Promise<BuildResult[]> {
    const res = await axios.get<BuildResult[]>(`${BUILD_BASE}/history/${projectId}?limit=${limit}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Download build artifact
  getDownloadUrl(projectId: string, targetOS: TargetOS, fileType: FileType): string {
    return `${BUILD_BASE}/download/${projectId}?targetOS=${targetOS}&fileType=${fileType}`;
  },

  // Get build logs
  async getBuildLogs(projectId: string, targetOS?: TargetOS): Promise<string[]> {
    const url = targetOS 
      ? `${BUILD_BASE}/logs/${projectId}?targetOS=${targetOS}`
      : `${BUILD_BASE}/logs/${projectId}`;
    const res = await axios.get<string[]>(url, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  }
};
