"use client";

import type { ChannelAnalysis } from "@/types/analysis";
import type { YouTubeCollectResult } from "@/types/youtube";
import { ActionChecklist } from "./ActionChecklist";
import { OverviewCards } from "./OverviewCards";
import { PerformanceCharts } from "./PerformanceCharts";
import { RecommendedVideos } from "./RecommendedVideos";

interface DashboardProps {
  collectResult: YouTubeCollectResult;
  analysis: ChannelAnalysis;
}

export function Dashboard({ collectResult, analysis }: DashboardProps) {
  return (
    <section className="flex flex-col gap-6" aria-label="채널 분석 대시보드">
      <OverviewCards collectResult={collectResult} analysis={analysis} />
      <PerformanceCharts collectResult={collectResult} />
      <RecommendedVideos recommendations={analysis.recommendedNextVideos} />
      <ActionChecklist items={analysis.actionChecklist} />
    </section>
  );
}
