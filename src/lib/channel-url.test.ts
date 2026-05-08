import { describe, expect, it } from "vitest";
import { parseChannelInput } from "./channel-url";

describe("parseChannelInput", () => {
  it.each([
    ["@creator", { type: "handle", value: "creator" }],
    ["https://www.youtube.com/@creator-123", { type: "handle", value: "creator-123" }],
    ["https://youtube.com/@creator.name_123/", { type: "handle", value: "creator.name_123" }],
    ["UC_x5XG1OV2P6uZZ5FSM9Ttw", { type: "channelId", value: "UC_x5XG1OV2P6uZZ5FSM9Ttw" }],
    [
      "https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw",
      { type: "channelId", value: "UC_x5XG1OV2P6uZZ5FSM9Ttw" },
    ],
    [
      "https://youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw/",
      { type: "channelId", value: "UC_x5XG1OV2P6uZZ5FSM9Ttw" },
    ],
  ])("parses supported input %s", (input, identifier) => {
    expect(parseChannelInput(input)).toEqual({ ok: true, identifier });
  });

  it.each([
    "",
    "   ",
    "https://www.youtube.com/c/creator",
    "https://youtube.com/user/creator",
    "https://www.youtube.com/watch?v=abc123",
    "https://youtube.com/shorts/abc123",
    "https://example.com/@creator",
    "https://youtu.be/abc123",
    "https://www.youtube.com/@",
    "UC",
  ])("rejects unsupported input %s", (input) => {
    expect(parseChannelInput(input)).toEqual({
      ok: false,
      error: {
        code: "INVALID_CHANNEL_URL",
        message: "지원하지 않는 YouTube URL 형식입니다.",
      },
    });
  });
});
