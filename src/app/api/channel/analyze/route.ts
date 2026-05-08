import { NextResponse } from "next/server";
import { toErrorResponse, type AppErrorResponse } from "@/lib/app-errors";
import { analyzeRequestSchema } from "@/lib/schemas";
import { analyzeChannelData } from "@/services/openai";

export const runtime = "nodejs";

export async function POST(request: Request) {
  let body: unknown;

  try {
    body = await request.json();
  } catch {
    return errorJson(toErrorResponse("OPENAI_PROVIDER_ERROR"));
  }

  const parsedRequest = analyzeRequestSchema.safeParse(body);

  if (!parsedRequest.success) {
    return errorJson(toErrorResponse("OPENAI_PROVIDER_ERROR"));
  }

  try {
    const result = await analyzeChannelData({
      ok: true,
      data: parsedRequest.data,
    });

    if (!result.ok) {
      return errorJson(result.error);
    }

    return NextResponse.json(result.data);
  } catch {
    return errorJson(toErrorResponse("OPENAI_PROVIDER_ERROR"));
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
