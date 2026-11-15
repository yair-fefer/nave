// GET /api/feed?hours=24  -> last contact, recent events, screenshot times (newest first).
import { redis, checkKey } from "./_lib.js";

export default async function handler(req, res) {
  if (!checkKey(req, res)) {
    return;
  }
  const hours = Math.min(48, Math.max(1, Number(req.query.hours) || 24));
  const now = Date.now();
  const times = await redis.zrange("shots", now - hours * 3600 * 1000, now, { byScore: true });
  const lastseen = await redis.get("lastseen");
  const events = await redis.lrange("events", 0, 19);
  res.setHeader("Cache-Control", "no-store");
  res.status(200).json({
    now,
    lastseen,
    events,
    shots: times.map(Number).sort((a, b) => b - a),
  });
}
