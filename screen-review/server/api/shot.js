// POST /api/shot  body = JPEG bytes. Stores one screenshot.
import { redis, checkKey, readBody, KEEP_SECONDS } from "./_lib.js";

export const config = { api: { bodyParser: false } };

export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).end();
  }
  if (!checkKey(req, res)) {
    return;
  }
  const body = await readBody(req);
  if (body.length < 100 || body.length > 3_000_000) {
    return res.status(400).json({ error: "bad image" });
  }
  const now = Date.now();
  // Screenshots that waited offline carry their real time in "x-time".
  const sent = Number(req.headers["x-time"]);
  let time = sent > now - KEEP_SECONDS * 1000 && sent <= now ? sent : now;
  while (await redis.exists(`shot:${time}`)) time += 1;
  await redis.set(`shot:${time}`, body.toString("base64"), { ex: KEEP_SECONDS });
  await redis.zadd("shots", { score: time, member: String(time) });
  await redis.zremrangebyscore("shots", 0, now - KEEP_SECONDS * 1000);
  await redis.set("lastseen", { time: now, state: "shot" });
  res.status(200).json({ ok: true, time });
}
