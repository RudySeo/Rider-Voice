"use client";

import { Activity, AlertCircle, CheckSquare, SignalHigh } from "lucide-react";
import type { ChannelAnalysis } from "@/types/analysis";
import type { YouTubeCollectResult } from "@/types/youtube";

interface OverviewCardsProps {
  collectResult: YouTubeCollectResult;
  analysis: ChannelAnalysis;
}

export function OverviewCards({ collectResult, analysis }: OverviewCardsProps) {
  const firstChecklist = analysis.actionChecklist[0]?.task ?? "우선순위 항목 없음";

  return (
    <section aria-labelledby="overview-heading" className="space-y-4">
      <div>
        <p className="text-sm font-semibold text-cyan-700">한눈에 보는 진단</p>
        <h2 id="overview-heading" className="mt-1 text-2xl font-semibold">
          {collectResult.channel.title}
        </h2>
      </div>

      <div className="grid gap-4 lg:grid-cols-[220px_1fr]">
        <article className="border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm font-medium text-slate-600">전체 점수</p>
            <Activity aria-hidden="true" className="text-cyan-700" size={20} />
          </div>
          <p className="mt-4 text-5xl font-semibold text-slate-950">
            {analysis.overallScore}
            <span className="text-xl text-slate-500">점</span>
          </p>
          <p className="mt-3 text-sm text-slate-600">
            신뢰도 {confidenceLabel[analysis.confidence.level]}
          </p>
        </article>

        <article className="border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-sm font-medium text-slate-600">3줄 요약</p>
          <ol className="mt-4 grid gap-3 text-sm leading-6 text-slate-800">
            {analysis.executiveSummary.map((summary, index) => (
              <li className="flex gap-3" key={summary}>
                <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center bg-slate-950 text-xs font-semibold text-white">
                  {index + 1}
                </span>
                <span>{summary}</span>
              </li>
            ))}
          </ol>
        </article>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <SignalCard
          icon={<SignalHigh aria-hidden="true" size={18} />}
          label="가장 강한 신호"
          value={analysis.strongSignals[0] ?? "강한 성과 신호 없음"}
        />
        <SignalCard
          icon={<AlertCircle aria-hidden="true" size={18} />}
          label="가장 위험한 병목"
          value={analysis.growthBottlenecks[0] ?? "성장 병목 없음"}
        />
        <SignalCard
          icon={<CheckSquare aria-hidden="true" size={18} />}
          label="이번 주 바로 할 일"
          value={firstChecklist}
        />
      </div>
    </section>
  );
}

function SignalCard({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <article className="min-h-36 border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center gap-2 text-cyan-700">
        {icon}
        <p className="text-sm font-semibold">{label}</p>
      </div>
      <p className="mt-4 text-sm leading-6 text-slate-800">{value}</p>
    </article>
  );
}

const confidenceLabel = {
  high: "높음",
  medium: "보통",
  low: "낮음",
} as const;
