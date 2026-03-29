import { useEffect, useRef, useState } from 'react';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { getAccessToken } from '../services/api';

interface BuildProgressProps {
  projectId: string;
  onComplete?: (downloadUrl: string | null) => void;
  onFailed?: (error: string) => void;
}

interface ProgressEvent {
  stage: string;
  message: string;
  progress?: number;
  downloadUrl?: string;
  error?: string;
}

const STAGE_ORDER = [
  'VALIDATING_ENV',
  'PREPARING',
  'WRITING_FILES',
  'INSTALLING',
  'BUILDING',
  'UPLOADING_R2',
  'COMPLETE',
];

const STAGE_LABELS: Record<string, string> = {
  VALIDATING_ENV: 'Validating environment',
  PREPARING: 'Preparing workspace',
  WRITING_FILES: 'Writing Electron files',
  INSTALLING: 'Installing dependencies',
  BUILDING: 'Building installer',
  UPLOADING_R2: 'Uploading to cloud',
  COMPLETE: 'Build complete',
  FAILED: 'Build failed',
};

export function BuildProgress({ projectId, onComplete, onFailed }: BuildProgressProps) {
  const [currentStage, setCurrentStage] = useState<string>('PREPARING');
  const [message, setMessage] = useState<string>('Starting build...');
  const [failed, setFailed] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const ctrl = new AbortController();
    abortRef.current = ctrl;

    fetchEventSource(`/conversion/conversions/${projectId}/build/stream`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` },
      signal: ctrl.signal,
      onmessage(ev) {
        try {
          const data: ProgressEvent = JSON.parse(ev.data);
          setCurrentStage(data.stage);
          setMessage(data.message);

          if (data.stage === 'COMPLETE') {
            setDone(true);
            onComplete?.(data.downloadUrl ?? null);
            ctrl.abort();
          } else if (data.stage === 'FAILED') {
            setFailed(true);
            setErrorMsg(data.error ?? data.message ?? 'Build failed');
            onFailed?.(data.error ?? data.message ?? 'Build failed');
            ctrl.abort();
          }
        } catch {
          // ignore parse errors
        }
      },
      onerror() {
        // SSE connection dropped — stop retrying
        ctrl.abort();
      },
    });

    return () => ctrl.abort();
  }, [projectId, onComplete, onFailed]);

  const stageIndex = STAGE_ORDER.indexOf(currentStage);
  const progressPct = done
    ? 100
    : stageIndex >= 0
    ? Math.round(((stageIndex + 1) / STAGE_ORDER.length) * 100)
    : 10;

  if (failed) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4">
        <div className="flex items-start gap-3">
          <span className="text-red-500 text-xl">✗</span>
          <div>
            <p className="font-semibold text-red-700">Build failed</p>
            {errorMsg && <p className="mt-1 text-sm text-red-600">{errorMsg}</p>}
          </div>
        </div>
      </div>
    );
  }

  if (done) {
    return (
      <div className="rounded-lg border border-green-200 bg-green-50 p-4">
        <div className="flex items-center gap-3">
          <span className="text-green-500 text-xl">✓</span>
          <p className="font-semibold text-green-700">Build complete! Your installer is ready to download.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4 space-y-3">
      <div className="flex items-center justify-between text-sm">
        <span className="font-medium text-gray-700">
          {STAGE_LABELS[currentStage] ?? currentStage}
        </span>
        <span className="text-gray-400">{progressPct}%</span>
      </div>

      <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
        <div
          className="h-full bg-indigo-500 rounded-full transition-all duration-500"
          style={{ width: `${progressPct}%` }}
        />
      </div>

      <p className="text-xs text-gray-500">{message}</p>

      <div className="flex gap-2 flex-wrap">
        {STAGE_ORDER.map((stage) => {
          const idx = STAGE_ORDER.indexOf(stage);
          const isActive = stage === currentStage;
          const isDone = idx < stageIndex || done;
          return (
            <span
              key={stage}
              className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                isDone
                  ? 'bg-indigo-100 text-indigo-700'
                  : isActive
                  ? 'bg-indigo-500 text-white'
                  : 'bg-gray-100 text-gray-400'
              }`}
            >
              {STAGE_LABELS[stage]}
            </span>
          );
        })}
      </div>
    </div>
  );
}
