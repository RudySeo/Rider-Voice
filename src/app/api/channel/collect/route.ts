import { NextResponse } from "next/server";
import { toErrorResponse, type AppErrorCode, type AppErrorResponse } from "@/lib/app-errors";
import { collectRequestSchema } from "@/lib/schemas";
import { collectChannelData } from "@/services/youtube";

export const runtime = "nodejs";

export async function POST(request: Request) {
  let body: unknown;

  try {
    body = await request.json();
  } catch {
    return errorJson(toErrorResponse("INVALID_CHANNEL_URL"));
  }

  const parsedRequest = collectRequestSchema.safeParse(body);

  if (!parsedRequest.success) {
    return errorJson(toErrorResponse("INVALID_CHANNEL_URL"));
  }

  try {
    const result = await collectChannelData(parsedRequest.data.channelUrl);

    if (!result.ok) {
      return errorJson(result.error);
    }

    return NextResponse.json(result.data);
  } catch {
    return errorJson(toErrorResponse("YOUTUBE_PROVIDER_ERROR"));
  }
}

function errorJson(errorResponse: AppErrorResponse) {
  return NextResponse.json(
    {
      error: errorResponse.error,
    },
    {
      status: errorResponse.status,
    },
  );
}

export type CollectRouteErrorCode = AppErrorCode;
