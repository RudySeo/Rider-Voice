import { DEFAULT_ERROR_MESSAGES } from "./app-errors";
import type {
  ChannelIdentifier,
  ParseChannelInputResult,
} from "@/types/youtube";

const YOUTUBE_HOSTS = new Set(["youtube.com", "www.youtube.com"]);
const HANDLE_PATTERN = /^@([A-Za-z0-9._-]+)$/;
const CHANNEL_ID_PATTERN = /^UC[A-Za-z0-9_-]{22}$/;

const invalidResult: ParseChannelInputResult = {
  ok: false,
  error: {
    code: "INVALID_CHANNEL_URL",
    message: DEFAULT_ERROR_MESSAGES.INVALID_CHANNEL_URL,
  },
};

export function parseChannelInput(input: string): ParseChannelInputResult {
  const value = input.trim();

  if (value.length === 0) {
    return invalidResult;
  }

  const directIdentifier = parseDirectIdentifier(value);

  if (directIdentifier) {
    return {
      ok: true,
      identifier: directIdentifier,
    };
  }

  return parseYoutubeUrl(value);
}

function parseDirectIdentifier(value: string): ChannelIdentifier | null {
  const handleMatch = value.match(HANDLE_PATTERN);

  if (handleMatch) {
    return {
      type: "handle",
      value: handleMatch[1],
    };
  }

  if (CHANNEL_ID_PATTERN.test(value)) {
    return {
      type: "channelId",
      value,
    };
  }

  return null;
}

function parseYoutubeUrl(value: string): ParseChannelInputResult {
  let parsedUrl: URL;

  try {
    parsedUrl = new URL(value);
  } catch {
    return invalidResult;
  }

  if (parsedUrl.protocol !== "https:" || !YOUTUBE_HOSTS.has(parsedUrl.hostname)) {
    return invalidResult;
  }

  const pathSegments = parsedUrl.pathname.split("/").filter(Boolean);

  if (pathSegments.length === 1) {
    return parseHandlePath(pathSegments[0]);
  }

  if (pathSegments.length === 2 && pathSegments[0] === "channel") {
    return parseChannelIdPath(pathSegments[1]);
  }

  return invalidResult;
}

function parseHandlePath(pathSegment: string): ParseChannelInputResult {
  const match = pathSegment.match(HANDLE_PATTERN);

  if (!match) {
    return invalidResult;
  }

  return {
    ok: true,
    identifier: {
      type: "handle",
      value: match[1],
    },
  };
}

function parseChannelIdPath(pathSegment: string): ParseChannelInputResult {
  if (!CHANNEL_ID_PATTERN.test(pathSegment)) {
    return invalidResult;
  }

  return {
    ok: true,
    identifier: {
      type: "channelId",
      value: pathSegment,
    },
  };
}
