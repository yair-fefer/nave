// GET /api/img?t=<time>  -> the JPEG of one screenshot.
import { redis, checkKey } from "./_lib.js";

export default async function handler(req, res) {
  if (!checkKey(req, res)) {
    return;
  }
  const t = String(req.query.t || "");
  if (!/^\d+$/.test(t)) {
    return res.status(400).end();
  }
  const data = await redis.get(`shot:${t}`);
  if (!data) {
    return res.status(404).end();
  }
  res.setHeader("Content-Type", "image/jpeg");
  res.setHeader("Cache-Control", "private, max-age=86400");
  res.status(200).send(Buffer.from(data, "base64"));
}
