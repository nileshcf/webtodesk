import { useState, useEffect, useCallback, useRef } from 'react';
import { buildApi } from '../services/buildApi';
import type { 
  BuildStatusResponse, 
  BuildProgress, 
  CrossPlatformBuildResult,
  BuildMetrics,
  TargetOS,
  FileType 
} from '../types/build';
import { BuildPriority } from '../types/build';
import { LicenseTier } from '../types/license';

export function useBuildQueue() {
  const [activeBuilds, setActiveBuilds] = useState<Map<string, BuildStatusResponse>>(new Map());
  const [buildProgress, setBuildProgress] = useState<Map<string, BuildProgress>>(new Map());
  const [queueStatus, setQueueStatus] = useState<{
    normalQueueLength: number;
    priorityQueueLength: number;
    averageWaitTime: number;
    estimatedPosition?: number;
  } | null>(null);
  const [metrics, setMetrics] = useState<BuildMetrics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const eventSources = useRef<Map<string, AbortController>>(new Map());

  // Subscribe to build progress
  const subscribeToBuildProgress = useCallback((projectId: string, targetOS?: TargetOS) => {
    // Clear any existing subscription
    const key = targetOS ? `${projectId}-${targetOS}` : projectId;
    if (eventSources.current.has(key)) {
      eventSources.current.get(key)?.abort();
    }

    const controller = new AbortController();
    eventSources.current.set(key, controller);

    const eventSource = buildApi.subscribeToBuildProgress(projectId, targetOS);

    eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        
        if (event.type === 'progress') {
          const progress = data as BuildProgress;
          setBuildProgress(prev => new Map(prev.set(key, progress)));
        } else if (event.type === 'status') {
          const status = data as BuildStatusResponse;
          setActiveBuilds(prev => new Map(prev.set(key, status)));
          
          // Clean up if build is complete or failed
          if (status.status === 'READY' || status.status === 'FAILED') {
            eventSource.close();
            controller.abort();
            eventSources.current.delete(key);
            setBuildProgress(prev => {
              const newMap = new Map(prev);
              newMap.delete(key);
              return newMap;
            });
          }
        }
      } catch (err) {
        console.error('Error parsing SSE data:', err);
      }
    };

    eventSource.onerror = (err) => {
      console.error('SSE error:', err);
      eventSource.close();
      controller.abort();
      eventSources.current.delete(key);
    };

    return () => {
      eventSource.close();
      controller.abort();
      eventSources.current.delete(key);
    };
  }, []);

  // Subscribe to cross-platform build progress
  const subscribeToCrossPlatformProgress = useCallback((projectId: string) => {
    const key = `cross-platform-${projectId}`;
    if (eventSources.current.has(key)) {
      eventSources.current.get(key)?.abort();
    }

    const controller = new AbortController();
    eventSources.current.set(key, controller);

    const eventSource = buildApi.subscribeToCrossPlatformProgress(projectId);

    eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        
        if (event.type === 'progress') {
          const progress = data as BuildProgress;
          setBuildProgress(prev => new Map(prev.set(key, progress)));
        } else if (event.type === 'result') {
          const result = data as CrossPlatformBuildResult;
          // Update individual OS build statuses
          Object.entries(result.results).forEach(([os, buildResult]) => {
            const osKey = `${projectId}-${os}`;
            if (buildResult.success) {
              setActiveBuilds(prev => new Map(prev.set(osKey, {
                projectId,
                projectName: result.overallSuccess ? 'Completed' : 'Partial',
                status: 'READY',
                buildError: null,
                downloadAvailable: true,
                downloadUrl: buildResult.downloadUrl || null,
                updatedAt: new Date().toISOString()
              })));
            }
          });
        }
      } catch (err) {
        console.error('Error parsing cross-platform SSE data:', err);
      }
    };

    eventSource.onerror = (err) => {
      console.error('Cross-platform SSE error:', err);
      eventSource.close();
      controller.abort();
      eventSources.current.delete(key);
    };

    return () => {
      eventSource.close();
      controller.abort();
      eventSources.current.delete(key);
    };
  }, []);

  // Trigger build
  const triggerBuild = useCallback(async (projectId: string, targetOS: TargetOS, fileType: FileType, priority: boolean = false) => {
    try {
      setLoading(true);
      setError(null);

      const buildRequest = {
        projectId,
        buildFlags: {
          targetOS,
          priority: priority ? BuildPriority.PRIORITY : BuildPriority.NORMAL,
          fileType,
          crossPlatform: false,
          osFileMappings: {
            WINDOWS: fileType,
            LINUX: fileType,
            MACOS: fileType
          }
        },
        featureConfig: {
          tier: LicenseTier.TRIAL, // This should come from license context
          buildFlags: {
            targetOS,
            priority: priority ? BuildPriority.PRIORITY : BuildPriority.NORMAL,
            fileType,
            crossPlatform: false,
            osFileMappings: {
              WINDOWS: fileType,
              LINUX: fileType,
              MACOS: fileType
            }
          },
          modules: {}
        }
      };

      const buildStatus = await buildApi.triggerBuild(buildRequest);
      setActiveBuilds(prev => new Map(prev.set(`${projectId}-${targetOS}`, buildStatus)));
      
      // Subscribe to progress
      subscribeToBuildProgress(projectId, targetOS);
      
      return buildStatus;
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to trigger build');
      throw err;
    } finally {
      setLoading(false);
    }
  }, [subscribeToBuildProgress]);

  // Trigger cross-platform build
  const triggerCrossPlatformBuild = useCallback(async (projectId: string, targetOSes: TargetOS[], fileTypes: Record<TargetOS, FileType>, priority: boolean = false) => {
    try {
      setLoading(true);
      setError(null);

      const buildRequest = {
        projectId,
        targetOSes,
        fileTypes,
        priority
      };

      const result = await buildApi.triggerCrossPlatformBuild(buildRequest);
      
      // Subscribe to progress
      subscribeToCrossPlatformProgress(projectId);
      
      return result;
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to trigger cross-platform build');
      throw err;
    } finally {
      setLoading(false);
    }
  }, [subscribeToCrossPlatformProgress]);

  // Get queue status
  const getQueueStatus = useCallback(async () => {
    try {
      const status = await buildApi.getQueueStatus();
      setQueueStatus(status);
      return status;
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to get queue status');
      throw err;
    }
  }, []);

  // Get build metrics
  const getBuildMetrics = useCallback(async (period: 'today' | 'week' | 'month' = 'month') => {
    try {
      const metricsData = await buildApi.getBuildMetrics(period);
      setMetrics(metricsData);
      return metricsData;
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to get build metrics');
      throw err;
    }
  }, []);

  // Cancel build
  const cancelBuild = useCallback(async (projectId: string, targetOS?: TargetOS) => {
    try {
      await buildApi.cancelBuild(projectId, targetOS);
      
      // Update local state
      const key = targetOS ? `${projectId}-${targetOS}` : projectId;
      setActiveBuilds(prev => {
        const newMap = new Map(prev);
        newMap.delete(key);
        return newMap;
      });
      
      // Clean up subscription
      if (eventSources.current.has(key)) {
        eventSources.current.get(key)?.abort();
        eventSources.current.delete(key);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to cancel build');
      throw err;
    }
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      eventSources.current.forEach(controller => controller.abort());
      eventSources.current.clear();
    };
  }, []);

  return {
    // State
    activeBuilds,
    buildProgress,
    queueStatus,
    metrics,
    loading,
    error,
    
    // Actions
    triggerBuild,
    triggerCrossPlatformBuild,
    subscribeToBuildProgress,
    subscribeToCrossPlatformProgress,
    getQueueStatus,
    getBuildMetrics,
    cancelBuild,
    
    // Computed
    isBuilding: activeBuilds.size > 0,
    queueLength: queueStatus?.normalQueueLength || 0,
    priorityQueueLength: queueStatus?.priorityQueueLength || 0,
    
    // Utility
    clearError: () => setError(null)
  };
}
