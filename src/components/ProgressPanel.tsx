"use client";

import { CheckCircle2, Circle, Loader2 } from "lucide-react";
import type { FlowStatus } from "./AnalysisShell";

const progressSteps = [
  "채널 찾는 중",
  "최근 업로드 읽는 중",
  "영상 성과 정리 중",
  "성과 패턴 찾는 중",
  "다음 액션 만드는 중",
] as const;

const activeStepByStatus: Partial<Record<FlowStatus, number>> = {
  validating: 0,
  collecting: 1,
  analyzing: 3,
  complete: 4,
};

interface ProgressPanelProps {
  status: FlowStatus;
}

export function ProgressPanel({ status }: ProgressPanelProps) {
  const activeStep = activeStepByStatus[status];

  if (activeStep === undefined) {
    return null;
  }

  return (
    <section
      aria-label="분석 진행 상태"
      aria-live="polite"
      className="border border-slate-200 bg-white p-5 shadow-sm"
    >
      <p className="text-sm font-semibold text-slate-900">{progressSteps[activeStep]}</p>
      <div className="mt-4 grid gap-3 sm:grid-cols-5">
        {progressSteps.map((step, index) => {
          const isComplete = status === "complete" || index < activeStep;
          const isActive = status !== "complete" && index === activeStep;

          return (
            <div
              className="flex min-h-10 items-center gap-2 text-sm text-slate-600"
              key={step}
            >
              {isComplete ? (
                <CheckCircle2 className="text-emerald-600" aria-hidden="true" size={17} />
              ) : isActive ? (
                <Loader2 className="animate-spin text-cyan-600" aria-hidden="true" size={17} />
              ) : (
                <Circle className="text-slate-300" aria-hidden="true" size={17} />
              )}
              <span className={isActive ? "font-semibold text-slate-950" : ""}>{step}</span>
            </div>
          );
        })}
      </div>
    </section>
  );
}
