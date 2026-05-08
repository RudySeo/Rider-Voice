import { describe, expect, it, vi } from "vitest";
import type { ChannelAnalysis } from "@/types/analysis";
import type { YouTubeCollectResult } from "@/types/youtube";
import { analyzeChannelData } from "./openai";

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
  strongSignals: ["조회수가 높은 포맷이 반복됩니다."],
  growthBottlenecks: ["업로드 간격이 불규칙합니다."],
  contentPatterns: ["실전 예제형 콘텐츠가 반응이 좋습니다."],
  recommendedNextVideos: [
    {
      titleDirection: "다음 영상 제목 방향",
      format: "튜토리얼",
      whyItFits: "기존 상위 영상과 포맷이 같습니다.",
      evidence: "First video가 평균 이상입니다.",
      expectedImpact: "재방문 시청자를 늘릴 수 있습니다.",
      difficulty: "low",
    },
  ],
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
    reason: "최근 공개 영상 1개 기준이라 표본이 작습니다.",
  },
};

describe("analyzeChannelData", () => {
  it("calls the Responses API with structured output schema and parses output text", async () => {
    const { fetchImpl, requests } = mockOpenAIFetch({
      output: [
        {
          type: "message",
          content: [
            {
              type: "output_text",
              text: JSON.stringify(analysisPayload),
            },
          ],
        },
      ],
    });

    const result = await analyzeChannelData(
      {
        ok: true,
        data: collectPayload,
      },
      {
        apiKey: "test-openai-key",
        model: "gpt-5.4-mini",
        fetchImpl,
      },
    );

    expect(result).toEqual({
      ok: true,
      data: analysisPayload,
    });

    expect(requests[0].url).toBe("https://api.openai.com/v1/responses");
    expect(requests[0].headers.authorization).toBe("Bearer test-openai-key");
    expect(requests[0].body.model).toBe("gpt-5.4-mini");
    expect(JSON.stringify(requests[0].body)).not.toContain("test-openai-key");
    expect(requests[0].body.text.format).toMatchObject({
      type: "json_schema",
      name: "channel_analysis",
      strict: true,
    });
    expect(requests[0].body.input[0].content).toContain("한국어");
    expect(requests[0].body.input[1].content).toContain("averageViewCount");
  });

  it("parses output_parsed responses", async () => {
    const result = await analyzeChannelData(
      {
        ok: true,
        data: collectPayload,
      },
      {
        apiKey: "test-openai-key",
        fetchImpl: mockOpenAIFetch({
          output_parsed: analysisPayload,
        }).fetchImpl,
      },
    );

    expect(result).toEqual({
      ok: true,
      data: analysisPayload,
    });
  });

  it("returns missing key, provider, and refusal errors with sanitized messages", async () => {
    await expect(
      analyzeChannelData({
        ok: true,
        data: collectPayload,
      }, { apiKey: "" }),
    ).resolves.toMatchObject({
      ok: false,
      error: {
        error: {
          code: "MISSING_OPENAI_API_KEY",
        },
        status: 500,
      },
    });

    await expect(
      analyzeChannelData(
        {
          ok: true,
          data: collectPayload,
        },
        {
          apiKey: "test-openai-key",
          fetchImpl: mockOpenAIFetch({ error: { message: "raw provider error" } }, 500)
            .fetchImpl,
        },
      ),
    ).resolves.toMatchObject({
      ok: false,
      error: {
        error: {
          code: "OPENAI_PROVIDER_ERROR",
          message: "AI 분석 요청을 처리하지 못했습니다.",
        },
        status: 502,
      },
    });

    await expect(
      analyzeChannelData(
        {
          ok: true,
          data: collectPayload,
        },
        {
          apiKey: "test-openai-key",
          fetchImpl: mockOpenAIFetch({
            output: [
              {
                type: "message",
                content: [
                  {
                    type: "refusal",
                    refusal: "I cannot comply.",
                  },
                ],
              },
            ],
          }).fetchImpl,
        },
      ),
    ).resolves.toMatchObject({
      ok: false,
      error: {
        error: {
          code: "OPENAI_REFUSAL",
        },
        status: 502,
      },
    });
  });
});

function mockOpenAIFetch(body: unknown, status = 200) {
  const requests: Array<{
    url: string;
    headers: Record<string, string>;
    body: OpenAIRequestBody;
  }> = [];

  const fetchImpl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const headers = Object.fromEntries(new Headers(init?.headers).entries());
    requests.push({
      url: input.toString(),
      headers,
      body: JSON.parse(String(init?.body ?? "{}")) as OpenAIRequestBody,
    });

    return new Response(JSON.stringify(body), {
      status,
      headers: {
        "content-type": "application/json",
      },
    });
  }) as unknown as typeof fetch;

  return {
    fetchImpl,
    requests,
  };
}

interface OpenAIRequestBody {
  model: string;
  input: Array<{
    role: string;
    content: string;
  }>;
  text: {
    format: {
      type: string;
      name: string;
      strict: boolean;
      schema: unknown;
    };
  };
}
