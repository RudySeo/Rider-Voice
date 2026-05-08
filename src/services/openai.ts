import "server-only";

import { toErrorResponse, type AppErrorResponse } from "@/lib/app-errors";
import {
  getAverageViewCount,
  getEngagementRate,
  getOutlierVideos,
  getTopVideos,
  getUploadCadenceDays,
} from "@/lib/metrics";
import { analyzeSuccessSchema } from "@/lib/schemas";
import type { ChannelAnalysis } from "@/types/analysis";
import type { YouTubeCollectResult, YouTubeVideo } from "@/types/youtube";
import type { CollectChannelDataResult } from "./youtube";

const OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
const DEFAULT_OPENAI_MODEL = "gpt-5.4-mini";

export type ChannelAnalysisResult =
  | {
      ok: true;
      data: ChannelAnalysis;
    }
  | {
      ok: false;
      error: AppErrorResponse;
    };

interface AnalyzeChannelDataOptions {
  apiKey?: string;
  model?: string;
  fetchImpl?: typeof fetch;
}

interface OpenAIResponseBody {
  output_text?: string;
  output_parsed?: unknown;
  output?: Array<{
    type?: string;
    content?: Array<{
      type?: string;
      text?: string;
      parsed?: unknown;
      refusal?: string;
    }>;
  }>;
}

export async function analyzeChannelData(
  input: CollectChannelDataResult,
  options: AnalyzeChannelDataOptions = {},
): Promise<ChannelAnalysisResult> {
  if (!input.ok) {
    return {
      ok: false,
      error: input.error,
    };
  }

  const apiKey = options.apiKey ?? process.env.OPENAI_API_KEY;

  if (!apiKey) {
    return {
      ok: false,
      error: toErrorResponse("MISSING_OPENAI_API_KEY"),
    };
  }

  const model = options.model ?? process.env.OPENAI_MODEL ?? DEFAULT_OPENAI_MODEL;
  const fetchImpl = options.fetchImpl ?? fetch;

  try {
    const response = await fetchImpl(OPENAI_RESPONSES_URL, {
      method: "POST",
      headers: {
        authorization: `Bearer ${apiKey}`,
        "content-type": "application/json",
      },
      body: JSON.stringify(buildOpenAIRequestBody(input.data, model)),
    });

    const responseBody = (await response.json().catch(() => ({}))) as OpenAIResponseBody;

    if (!response.ok) {
      return {
        ok: false,
        error: toErrorResponse("OPENAI_PROVIDER_ERROR"),
      };
    }

    const parsedAnalysis = extractStructuredAnalysis(responseBody);

    if (!parsedAnalysis) {
      return {
        ok: false,
        error: toErrorResponse("OPENAI_REFUSAL", undefined, 502),
      };
    }

    return {
      ok: true,
      data: parsedAnalysis,
    };
  } catch {
    return {
      ok: false,
      error: toErrorResponse("OPENAI_PROVIDER_ERROR"),
    };
  }
}

function buildOpenAIRequestBody(input: YouTubeCollectResult, model: string) {
  return {
    model,
    input: [
      {
        role: "system",
        content: buildSystemPrompt(),
      },
      {
        role: "user",
        content: JSON.stringify(buildAnalysisInput(input)),
      },
    ],
    text: {
      format: {
        type: "json_schema",
        name: "channel_analysis",
        strict: true,
        schema: analysisJsonSchema,
      },
    },
  };
}

function buildSystemPrompt() {
  return [
    "당신은 YouTube 채널 운영자를 위한 한국어 콘텐츠 전략 분석가입니다.",
    "수집된 공개 채널 데이터만 근거로 분석하고, 데이터 기반 근거와 AI 추론을 구분해 표현하세요.",
    "핵심 질문은 '다음에 무엇을 만들까?'입니다.",
    "추상적인 조언보다 다음 영상 제작 결정, 반복할 패턴, 줄일 행동, 실행 체크리스트를 우선하세요.",
    "최근 공개 영상 sample size가 작거나 주요 통계가 누락되면 confidence를 낮추고 이유를 설명하세요.",
    "반드시 제공된 JSON schema를 만족하는 JSON만 반환하세요.",
  ].join("\n");
}

function buildAnalysisInput(input: YouTubeCollectResult) {
  const topVideos = getTopVideos(input.videos, 10);
  const outlierVideos = getOutlierVideos(input.videos);

  return {
    channel: input.channel,
    sampleSize: input.sampleSize,
    collectedAt: input.collectedAt,
    metrics: {
      averageViewCount: getAverageViewCount(input.videos),
      uploadCadenceDays: getUploadCadenceDays(input.videos),
      topVideos: topVideos.map(toVideoMetricSnapshot),
      outlierVideos: outlierVideos.map(toVideoMetricSnapshot),
    },
    videos: input.videos.map(toVideoMetricSnapshot),
  };
}

function toVideoMetricSnapshot(video: YouTubeVideo) {
  return {
    id: video.id,
    title: video.title,
    description: video.description,
    publishedAt: video.publishedAt,
    viewCount: video.viewCount,
    likeCount: video.likeCount,
    commentCount: video.commentCount,
    duration: video.duration,
    engagementRate: getEngagementRate(video),
  };
}

function extractStructuredAnalysis(responseBody: OpenAIResponseBody): ChannelAnalysis | null {
  if (responseBody.output_parsed) {
    return parseCandidate(responseBody.output_parsed);
  }

  if (typeof responseBody.output_text === "string") {
    return parseCandidateText(responseBody.output_text);
  }

  for (const outputItem of responseBody.output ?? []) {
    for (const contentItem of outputItem.content ?? []) {
      if (contentItem.type === "refusal" || contentItem.refusal) {
        return null;
      }

      if (contentItem.parsed) {
        const parsed = parseCandidate(contentItem.parsed);

        if (parsed) {
          return parsed;
        }
      }

      if (typeof contentItem.text === "string") {
        const parsed = parseCandidateText(contentItem.text);

        if (parsed) {
          return parsed;
        }
      }
    }
  }

  return null;
}

function parseCandidateText(text: string): ChannelAnalysis | null {
  try {
    return parseCandidate(JSON.parse(text));
  } catch {
    return null;
  }
}

function parseCandidate(candidate: unknown): ChannelAnalysis | null {
  const parsed = analyzeSuccessSchema.safeParse(candidate);

  return parsed.success ? parsed.data : null;
}

const analysisJsonSchema = {
  type: "object",
  additionalProperties: false,
  required: [
    "overallScore",
    "executiveSummary",
    "strongSignals",
    "growthBottlenecks",
    "contentPatterns",
    "recommendedNextVideos",
    "actionChecklist",
    "confidence",
  ],
  properties: {
    overallScore: {
      type: "integer",
      minimum: 0,
      maximum: 100,
    },
    executiveSummary: {
      type: "array",
      minItems: 3,
      maxItems: 3,
      items: {
        type: "string",
      },
    },
    strongSignals: {
      type: "array",
      items: {
        type: "string",
      },
    },
    growthBottlenecks: {
      type: "array",
      items: {
        type: "string",
      },
    },
    contentPatterns: {
      type: "array",
      items: {
        type: "string",
      },
    },
    recommendedNextVideos: {
      type: "array",
      minItems: 1,
      items: {
        type: "object",
        additionalProperties: false,
        required: [
          "titleDirection",
          "format",
          "whyItFits",
          "evidence",
          "expectedImpact",
          "difficulty",
        ],
        properties: {
          titleDirection: {
            type: "string",
          },
          format: {
            type: "string",
          },
          whyItFits: {
            type: "string",
          },
          evidence: {
            type: "string",
          },
          expectedImpact: {
            type: "string",
          },
          difficulty: {
            type: "string",
            enum: ["low", "medium", "high"],
          },
        },
      },
    },
    actionChecklist: {
      type: "array",
      maxItems: 7,
      items: {
        type: "object",
        additionalProperties: false,
        required: ["priority", "task", "reason", "expectedImpact"],
        properties: {
          priority: {
            type: "string",
            enum: ["high", "medium", "low"],
          },
          task: {
            type: "string",
          },
          reason: {
            type: "string",
          },
          expectedImpact: {
            type: "string",
          },
        },
      },
    },
    confidence: {
      type: "object",
      additionalProperties: false,
      required: ["level", "reason"],
      properties: {
        level: {
          type: "string",
          enum: ["high", "medium", "low"],
        },
        reason: {
          type: "string",
        },
      },
    },
  },
} as const;
