import type { AppErrorCode } from "@/lib/app-errors";
import type { ChannelAnalysis } from "./analysis";
import type { YouTubeCollectResult } from "./youtube";

export interface CollectRequest {
  channelUrl: string;
}

export type CollectSuccessResponse = YouTubeCollectResult;

export type AnalyzeRequest = YouTubeCollectResult;

export type AnalyzeSuccessResponse = ChannelAnalysis;

export interface ApiErrorResponse {
  error: {
    code: AppErrorCode;
    message: string;
  };
}

export type ApiResponse<TSuccess> = TSuccess | ApiErrorResponse;
