import { beforeEach, describe, expect, it, vi } from "vitest";
import { toErrorResponse } from "@/lib/app-errors";
import type { ChannelAnalysis } from "@/types/analysis";
import type { YouTubeCollectResult } from "@/types/youtube";
import { analyzeChannelData } from "@/services/openai";
import { POST } from "./route";

vi.mock("@/services/openai", () => ({
  analyzeChannelData: vi.fn(),
}));

const analyzeChannelDataMock = vi.mocked(analyzeChannelData);

const collectPayload: YouTubeCollectResult = {
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
    {
      id: "video-a",
      title: "First video",
      description: "First description",
      publishedAt: "2026-05-08T00:00:00Z",
      thumbnailUrl: null,
      viewCount: 100,
      likeCount: 10,
      commentCount: 1,
      duration: "PT10M",
    },
  ],
  sampleSize: 1,
  collectedAt: "2026-05-08T00:00:00Z",
};

const analysisPayload: ChannelAnalysis = {
  overallScore: 82,
  executiveSummary: ["요약 1", "요약 2", "요약 3"],
  strongSignals: ["강한 성과 신호"],
  growthBottlenecks: ["성장 병목"],
  contentPatterns: ["콘텐츠 패턴"],
  recommendedNextVideos: [
    {
      titleDirection: "추천 제목 방향",
      format: "튜토리얼",
      whyItFits: "상위 영상과 맞습니다.",
      evidence: "기존 영상 성과",
      expectedImpact: "조회수 개선",
      difficulty: "low",
    },
  ],
  actionChecklist: [
    {
      priority: "high",
      task: "반복할 패턴 정리",
      reason: "성과 근거가 있습니다.",
      expectedImpact: "기획 속도 개선",
    },
  ],
  confidence: {
    level: "medium",
    reason: "표본이 작습니다.",
  },
};

describe("POST /api/channel/analyze", () => {
  beforeEach(() => {
    analyzeChannelDataMock.mockReset();
  });

  it("validates collect payloads and returns analysis data", async () => {
    analyzeChannelDataMock.mockResolvedValue({
      ok: true,
      data: analysisPayload,
    });

    const response = await POST(jsonRequest(collectPayload));

    await expect(response.json()).resolves.toEqual(analysisPayload);
    expect(response.status).toBe(200);
    expect(analyzeChannelDataMock).toHaveBeenCalledWith({
      ok: true,
      data: collectPayload,
    });
  });

  it("rejects invalid request payloads without calling the service", async () => {
    const response = await POST(jsonRequest({ videos: [] }));

    await expect(response.json()).resolves.toMatchObject({
      error: {
        code: "OPENAI_PROVIDER_ERROR",
      },
    });
    expect(response.status).toBe(502);
    expect(analyzeChannelDataMock).not.toHaveBeenCalled();
  });

  it("maps service errors to sanitized JSON responses", async () => {
    analyzeChannelDataMock.mockResolvedValue({
      ok: false,
      error: toErrorResponse("OPENAI_REFUSAL", undefined, 502),
    });

    const response = await POST(jsonRequest(collectPayload));

    await expect(response.json()).resolves.toEqual({
      error: {
        code: "OPENAI_REFUSAL",
        message: "AI 분석 결과를 생성하지 못했습니다.",
      },
    });
    expect(response.status).toBe(502);
  });
});

function jsonRequest(body: unknown) {
  return new Request("http://localhost/api/channel/analyze", {
    method: "POST",
    body: JSON.stringify(body),
    headers: {
      "content-type": "application/json",
    },
  });
}
