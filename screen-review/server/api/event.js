// POST /api/event?type=ping|screen_off|start|stop|reboot
// Heartbeat and status changes from the phone.
import { redis, checkKey } from "./_lib.js";

const TYPES = ["ping", "screen_off", "start", "stop", "reboot"];

export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).end();
  }
  if (!checkKey(req, res)) {
    return;
  }
  const type = String(req.query.type || "");
  if (!TYPES.includes(type)) {
    return res.status(400).json({ error: "bad type" });
  }
  const now = Date.now();
  await redis.set("lastseen", { time: now, state: type });
  if (type !== "ping" && type !== "screen_off") {
    await redis.lpush("events", { time: now, type });
    await redis.ltrim("events", 0, 49);
  }
  res.status(200).json({ ok: true });
}
