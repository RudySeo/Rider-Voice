import { describe, expect, it } from "vitest";
import {
  analyzeRequestSchema,
  analyzeSuccessSchema,
  apiErrorSchema,
  collectRequestSchema,
  collectSuccessSchema,
} from "./schemas";

const collectPayload = {
  channel: {
    id: "UC_x5XG1OV2P6uZZ5FSM9Ttw",
    title: "Google for Developers",
    description: "Developer videos",
    thumbnailUrl: null,
    subscriberCount: null,
    viewCount: 1_000_000,
    videoCount: null,
  },
  videos: [
    {
      id: "video-id",
      title: "Video title",
      description: "Video description",
      publishedAt: "2026-05-08T00:00:00Z",
      thumbnailUrl: "https://img.youtube.com/vi/video-id/default.jpg",
      viewCount: null,
      likeCount: 50,
      commentCount: null,
      duration: "PT10M",
    },
  ],
  sampleSize: 1,
  collectedAt: "2026-05-08T00:00:00Z",
};

const analysisPayload = {
  overallScore: 82,
  executiveSummary: ["요약 1", "요약 2", "요약 3"],
  strongSignals: ["강한 성과 신호"],
  growthBottlenecks: ["성장 병목"],
  contentPatterns: ["반복 가능한 콘텐츠 패턴"],
  recommendedNextVideos: [
    {
      titleDirection: "추천 제목 방향",
      format: "추천 포맷",
      whyItFits: "이 채널에 맞는 이유",
      evidence: "근거가 된 기존 영상 패턴",
      expectedImpact: "기대 효과",
      difficulty: "low",
    },
  ],
  actionChecklist: [
    {
      priority: "high",
      task: "해야 할 일",
      reason: "이유",
      expectedImpact: "예상 효과",
    },
  ],
  confidence: {
    level: "medium",
    reason: "최근 공개 영상 37개 기준",
  },
};

describe("schemas", () => {
  it("validates collect requests", () => {
    expect(collectRequestSchema.safeParse({ channelUrl: "@handle" }).success).toBe(true);
    expect(collectRequestSchema.safeParse({ channelUrl: "" }).success).toBe(false);
  });

  it("validates collect success payloads with nullable provider counts", () => {
    expect(collectSuccessSchema.parse(collectPayload)).toEqual(collectPayload);
  });

  it("uses the collect payload as the analyze request contract", () => {
    expect(analyzeRequestSchema.parse(collectPayload)).toEqual(collectPayload);
  });

  it("validates analyze success payloads", () => {
    expect(analyzeSuccessSchema.parse(analysisPayload)).toEqual(analysisPayload);
  });

  it("rejects invalid recommendation difficulty and oversized checklists", () => {
    expect(
      analyzeSuccessSchema.safeParse({
        ...analysisPayload,
        recommendedNextVideos: [
          {
            ...analysisPayload.recommendedNextVideos[0],
            difficulty: "extreme",
          },
        ],
      }).success,
    ).toBe(false);

    expect(
      analyzeSuccessSchema.safeParse({
        ...analysisPayload,
        actionChecklist: Array.from({ length: 8 }, (_, index) => ({
          ...analysisPayload.actionChecklist[0],
          task: `Task ${index}`,
        })),
      }).success,
    ).toBe(false);
  });

  it("validates sanitized API error responses", () => {
    expect(
      apiErrorSchema.parse({
        error: {
          code: "CHANNEL_NOT_FOUND",
          message: "채널을 찾지 못했습니다.",
        },
      }),
    ).toEqual({
      error: {
        code: "CHANNEL_NOT_FOUND",
        message: "채널을 찾지 못했습니다.",
      },
    });
  });
});
