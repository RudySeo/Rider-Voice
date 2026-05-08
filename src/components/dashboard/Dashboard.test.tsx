import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ChannelAnalysis } from "@/types/analysis";
import type { YouTubeCollectResult } from "@/types/youtube";
import { Dashboard } from "./Dashboard";

const collectResult: YouTubeCollectResult = {
  channel: {
    id: "UC_x5XG1OV2P6uZZ5FSM9Ttw",
    title: "Google for Developers",
    description: "Developer videos",
    thumbnailUrl: null,
    subscriberCount: 1000,
    viewCount: 500000,
    videoCount: 120,
  },
  videos: [
    video("video-a", "튜토리얼로 배우는 API", "2026-05-01T00:00:00Z", 100, 10, 1),
    video("video-b", "실전 프로젝트 분석", "2026-05-04T00:00:00Z", 900, 90, 9),
    video("video-c", "짧은 업데이트", "2026-05-08T00:00:00Z", 120, null, null),
  ],
  sampleSize: 3,
  collectedAt: "2026-05-08T00:00:00Z",
};

const analysis: ChannelAnalysis = {
  overallScore: 82,
  executiveSummary: ["요약 1", "요약 2", "요약 3"],
  strongSignals: ["실전 프로젝트 분석 영상이 평균 대비 크게 튀었습니다."],
  growthBottlenecks: ["업로드 간격이 아직 안정적이지 않습니다."],
  contentPatterns: ["튜토리얼과 실전 분석 조합이 반복 가능한 패턴입니다."],
  recommendedNextVideos: Array.from({ length: 5 }, (_, index) => ({
    titleDirection: `추천 제목 방향 ${index + 1}`,
    format: "튜토리얼",
    whyItFits: "기존 상위 영상과 맞습니다.",
    evidence: "실전 프로젝트 분석이 높은 조회수를 만들었습니다.",
    expectedImpact: "다음 영상 선택 시간을 줄입니다.",
    difficulty: index % 2 === 0 ? "low" : "medium",
  })),
  actionChecklist: [
    {
      priority: "high",
      task: "상위 영상의 제목 구조를 반복한다.",
      reason: "성과가 검증된 패턴입니다.",
      expectedImpact: "클릭률 개선 가능성이 있습니다.",
    },
  ],
  confidence: {
    level: "medium",
    reason: "최근 공개 영상 3개 기준입니다.",
  },
};

describe("Dashboard", () => {
  it("renders score, summary, recommendations, and checklist", () => {
    render(<Dashboard collectResult={collectResult} analysis={analysis} />);

    expect(screen.getByRole("heading", { name: "Google for Developers" })).toBeInTheDocument();
    expect(screen.getByText("82")).toBeInTheDocument();
    expect(screen.getByText("요약 1")).toBeInTheDocument();
    expect(screen.getByText("추천 제목 방향 5")).toBeInTheDocument();
    expect(screen.getAllByText("상위 영상의 제목 구조를 반복한다.").length).toBeGreaterThan(0);
  });

  it("keeps performance charts stable with short video lists", () => {
    render(
      <Dashboard
        collectResult={{
          ...collectResult,
          videos: [video("video-a", "단일 영상", "2026-05-01T00:00:00Z", 100, 10, 1)],
          sampleSize: 1,
        }}
        analysis={analysis}
      />,
    );

    expect(screen.getByText("단일 영상")).toBeInTheDocument();
    expect(screen.getByText("업로드 간격을 계산할 데이터가 부족합니다.")).toBeInTheDocument();
  });

  it("does not crash when metric fields are nullable", () => {
    render(
      <Dashboard
        collectResult={{
          ...collectResult,
          videos: [
            video("video-a", "비공개 통계 영상", "2026-05-01T00:00:00Z", null, null, null),
          ],
          sampleSize: 1,
        }}
        analysis={analysis}
      />,
    );

    expect(screen.getAllByText("데이터 없음").length).toBeGreaterThan(0);
    expect(screen.getByText("비공개 통계 영상")).toBeInTheDocument();
  });

  it("keeps core text available in narrow layouts", () => {
    render(<Dashboard collectResult={collectResult} analysis={analysis} />);

    expect(screen.getByText("다음 영상 아이디어")).toBeInTheDocument();
    expect(screen.getByText("이번 주 액션")).toBeInTheDocument();
    expect(screen.getByText("추천 제목 방향 1")).toBeInTheDocument();
  });
});

function video(
  id: string,
  title: string,
  publishedAt: string,
  viewCount: number | null,
  likeCount: number | null,
  commentCount: number | null,
) {
  return {
    id,
    title,
    description: `${title} description`,
    publishedAt,
    thumbnailUrl: null,
    viewCount,
    likeCount,
    commentCount,
    duration: "PT10M",
  };
}
