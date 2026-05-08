import { describe, expect, it } from "vitest";
import { APP_ERROR_CODES, toErrorResponse } from "./app-errors";

describe("app errors", () => {
  it("defines every MVP error code", () => {
    expect(APP_ERROR_CODES).toEqual([
      "INVALID_CHANNEL_URL",
      "MISSING_YOUTUBE_API_KEY",
      "MISSING_OPENAI_API_KEY",
      "CHANNEL_NOT_FOUND",
      "NO_PUBLIC_VIDEOS",
      "YOUTUBE_RATE_LIMITED",
      "YOUTUBE_PROVIDER_ERROR",
      "OPENAI_REFUSAL",
      "OPENAI_PROVIDER_ERROR",
    ]);
  });

  it("builds a sanitized default error response", () => {
    expect(toErrorResponse("INVALID_CHANNEL_URL")).toEqual({
      error: {
        code: "INVALID_CHANNEL_URL",
        message: "지원하지 않는 YouTube URL 형식입니다.",
      },
      status: 400,
    });
  });

  it("allows caller-provided safe message and status overrides", () => {
    expect(
      toErrorResponse(
        "YOUTUBE_PROVIDER_ERROR",
        "YouTube 데이터를 불러오지 못했습니다.",
        503,
      ),
    ).toEqual({
      error: {
        code: "YOUTUBE_PROVIDER_ERROR",
        message: "YouTube 데이터를 불러오지 못했습니다.",
      },
      status: 503,
    });
  });
});
