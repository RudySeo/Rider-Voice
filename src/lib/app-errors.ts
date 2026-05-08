export const APP_ERROR_CODES = [
  "INVALID_CHANNEL_URL",
  "MISSING_YOUTUBE_API_KEY",
  "MISSING_OPENAI_API_KEY",
  "CHANNEL_NOT_FOUND",
  "NO_PUBLIC_VIDEOS",
  "YOUTUBE_RATE_LIMITED",
  "YOUTUBE_PROVIDER_ERROR",
  "OPENAI_REFUSAL",
  "OPENAI_PROVIDER_ERROR",
] as const;

export type AppErrorCode = (typeof APP_ERROR_CODES)[number];

export interface AppErrorResponse {
  error: {
    code: AppErrorCode;
    message: string;
  };
  status: number;
}

export const DEFAULT_ERROR_MESSAGES: Record<AppErrorCode, string> = {
  INVALID_CHANNEL_URL: "지원하지 않는 YouTube URL 형식입니다.",
  MISSING_YOUTUBE_API_KEY: "서버에 YouTube API key가 설정되어 있지 않습니다.",
  MISSING_OPENAI_API_KEY: "서버에 OpenAI API key가 설정되어 있지 않습니다.",
  CHANNEL_NOT_FOUND: "공개 YouTube 채널을 찾지 못했습니다.",
  NO_PUBLIC_VIDEOS: "최근 공개 업로드 영상을 찾지 못했습니다.",
  YOUTUBE_RATE_LIMITED: "YouTube 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.",
  YOUTUBE_PROVIDER_ERROR: "YouTube 데이터를 불러오지 못했습니다.",
  OPENAI_REFUSAL: "AI 분석 결과를 생성하지 못했습니다.",
  OPENAI_PROVIDER_ERROR: "AI 분석 요청을 처리하지 못했습니다.",
};

const DEFAULT_ERROR_STATUSES: Record<AppErrorCode, number> = {
  INVALID_CHANNEL_URL: 400,
  MISSING_YOUTUBE_API_KEY: 500,
  MISSING_OPENAI_API_KEY: 500,
  CHANNEL_NOT_FOUND: 404,
  NO_PUBLIC_VIDEOS: 404,
  YOUTUBE_RATE_LIMITED: 429,
  YOUTUBE_PROVIDER_ERROR: 502,
  OPENAI_REFUSAL: 422,
  OPENAI_PROVIDER_ERROR: 502,
};

export function toErrorResponse(
  code: AppErrorCode,
  message = DEFAULT_ERROR_MESSAGES[code],
  status = DEFAULT_ERROR_STATUSES[code],
): AppErrorResponse {
  return {
    error: {
      code,
      message,
    },
    status,
  };
}
