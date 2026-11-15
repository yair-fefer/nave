// Shared helpers: Redis client, key check, constants.
import { Redis } from "@upstash/redis";

export const KEEP_SECONDS = 48 * 3600; // screenshots are deleted after 48 hours

export const redis = new Redis({
  url: process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL,
  token: process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN,
});

// Every request must carry the shared app key in the "x-key" header.
export function checkKey(req, res) {
  const expected = process.env.APP_KEY;
  if (!expected || req.headers["x-key"] !== expected) {
    res.status(401).json({ error: "unauthorized" });
    return false;
  }
  return true;
}

export async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}
