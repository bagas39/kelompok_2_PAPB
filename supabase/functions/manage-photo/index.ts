import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const supabase = createClient(supabaseUrl, supabaseServiceKey, {
  auth: {
    persistSession: false,
    autoRefreshToken: false,
  },
});

const PHOTO_BUCKET = "clothing-photos";
const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const MAX_DELETE_BATCH = 10;
const SESSION_TOKEN_REGEX = /^[\x21-\x7E]{10,200}$/;
const OBJECT_PATH_REGEX = /^[0-9]+\/[A-Za-z0-9._-]+\.(jpg|jpeg|png|webp)$/i;

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-session-token",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...CORS_HEADERS,
    },
  });
}

function detectImageFormat(bytes: Uint8Array): { mime: string; ext: string } | null {
  if (
    bytes.length >= 3 &&
    bytes[0] === 0xff &&
    bytes[1] === 0xd8 &&
    bytes[2] === 0xff
  ) {
    return { mime: "image/jpeg", ext: "jpg" };
  }

  if (
    bytes.length >= 8 &&
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47 &&
    bytes[4] === 0x0d &&
    bytes[5] === 0x0a &&
    bytes[6] === 0x1a &&
    bytes[7] === 0x0a
  ) {
    return { mime: "image/png", ext: "png" };
  }

  if (
    bytes.length >= 12 &&
    bytes[0] === 0x52 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x46 &&
    bytes[8] === 0x57 &&
    bytes[9] === 0x45 &&
    bytes[10] === 0x42 &&
    bytes[11] === 0x50
  ) {
    return { mime: "image/webp", ext: "webp" };
  }

  return null;
}

async function resolveAuthenticatedUserId(req: Request): Promise<number | null> {
  const token = (req.headers.get("x-session-token") ?? "").trim();
  if (!SESSION_TOKEN_REGEX.test(token)) {
    return null;
  }

  const nowMs = Date.now();
  const { data, error } = await supabase
    .from("user_sessions")
    .select("userId, expiresAt")
    .eq("token", token)
    .gt("expiresAt", nowMs)
    .maybeSingle();

  if (error || !data) {
    return null;
  }

  const userId = Number(data.userId ?? 0);
  if (!Number.isSafeInteger(userId) || userId <= 0) {
    return null;
  }

  return userId;
}

async function handleUpload(req: Request, userId: number, kindParam: string | null): Promise<Response> {
  const contentLengthHeader = req.headers.get("content-length");
  if (contentLengthHeader) {
    const declaredLength = Number(contentLengthHeader);
    if (Number.isFinite(declaredLength) && declaredLength > MAX_PHOTO_BYTES) {
      return jsonResponse({ code: "PAYLOAD_TOO_LARGE", message: "Ukuran foto maksimal 5 MB" });
    }
  }

  const bytes = new Uint8Array(await req.arrayBuffer());
  if (bytes.byteLength === 0) {
    return jsonResponse({ code: "INVALID_FILE", message: "File foto kosong" });
  }
  if (bytes.byteLength > MAX_PHOTO_BYTES) {
    return jsonResponse({ code: "PAYLOAD_TOO_LARGE", message: "Ukuran foto maksimal 5 MB" });
  }

  const detected = detectImageFormat(bytes);
  if (!detected) {
    return jsonResponse({
      code: "INVALID_FILE_TYPE",
      message: "Format foto harus JPG, PNG, atau WebP",
    });
  }

  const prefix = kindParam === "avatar" ? "avatar" : "photo";
  const objectPath = `${userId}/${prefix}_${crypto.randomUUID()}.${detected.ext}`;

  const { error: uploadError } = await supabase.storage
    .from(PHOTO_BUCKET)
    .upload(objectPath, bytes, {
      contentType: detected.mime,
      upsert: false,
    });

  if (uploadError) {
    return jsonResponse({
      code: "UPLOAD_FAILED",
      message: "Gagal mengunggah foto ke storage",
    });
  }

  const cleanBaseUrl = supabaseUrl.replace(/\/+$/, "");
  const publicUrl = `${cleanBaseUrl}/storage/v1/object/public/${PHOTO_BUCKET}/${objectPath}`;
  return jsonResponse({
    code: "SUCCESS",
    publicUrl,
    objectPath,
  });
}

async function handleDelete(req: Request, userId: number): Promise<Response> {
  let payload: Record<string, unknown>;
  try {
    payload = (await req.json()) as Record<string, unknown>;
  } catch (_err) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: "Format permintaan hapus foto tidak valid" });
  }

  if (payload?.action !== "delete") {
    return jsonResponse({ code: "VALIDATION_FAILED", message: "Aksi pengelolaan foto tidak dikenal" });
  }

  const rawUris = Array.isArray(payload?.photoUris) ? payload.photoUris.slice(0, MAX_DELETE_BATCH) : [];
  const cleanBaseUrl = supabaseUrl.replace(/\/+$/, "");
  const expectedPrefix = `${cleanBaseUrl}/storage/v1/object/public/${PHOTO_BUCKET}/`;

  const candidates: Array<{ uri: string; objectPath: string }> = [];
  const seenUris = new Set<string>();

  for (const raw of rawUris) {
    if (typeof raw !== "string") continue;
    const uri = raw.trim();
    if (!uri || seenUris.has(uri) || !uri.startsWith(expectedPrefix)) continue;

    const objectPath = uri.slice(expectedPrefix.length);
    if (
      !objectPath.startsWith(`${userId}/`) ||
      objectPath.includes("..") ||
      objectPath.includes("\\") ||
      objectPath.includes("?") ||
      objectPath.includes("#") ||
      !OBJECT_PATH_REGEX.test(objectPath)
    ) {
      continue;
    }

    seenUris.add(uri);
    candidates.push({ uri, objectPath });
  }

  if (candidates.length === 0) {
    return jsonResponse({
      code: "SUCCESS",
      deletedPaths: [],
      skippedCount: 0,
    });
  }

  const { data: filterResp, error: filterError } = await supabase.rpc(
    "rpc_filter_deletable_photo_uris",
    {
      p_user_id: userId,
      p_photo_uris: candidates.map((c) => c.uri),
    },
  );

  if (filterError || !filterResp || filterResp.code !== "SUCCESS") {
    return jsonResponse({
      code: "ERROR",
      message: "Gagal memverifikasi referensi foto di database",
    });
  }

  const deletableSet = new Set<string>(
    Array.isArray(filterResp.deletableUris)
      ? filterResp.deletableUris.map((item: unknown) => String(item))
      : [],
  );

  const pathsToRemove = candidates
    .filter((candidate) => deletableSet.has(candidate.uri))
    .map((candidate) => candidate.objectPath);

  if (pathsToRemove.length > 0) {
    await supabase.storage.from(PHOTO_BUCKET).remove(pathsToRemove);
  }

  return jsonResponse({
    code: "SUCCESS",
    deletedPaths: pathsToRemove,
    skippedCount: candidates.length - pathsToRemove.length,
  });
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  if (req.method !== "POST") {
    return jsonResponse({ code: "METHOD_NOT_ALLOWED", message: "Gunakan metode POST" }, 405);
  }

  if (!supabaseUrl || !supabaseServiceKey) {
    return jsonResponse({ code: "SERVER_MISCONFIGURED", message: "Konfigurasi server belum lengkap" });
  }

  try {
    const userId = await resolveAuthenticatedUserId(req);
    if (userId === null) {
      return jsonResponse({
        code: "UNAUTHORIZED",
        message: "Sesi tidak valid atau sudah berakhir",
      });
    }

    const url = new URL(req.url);
    const actionParam = url.searchParams.get("action");
    if (actionParam === "upload") {
      return await handleUpload(req, userId, url.searchParams.get("kind"));
    }

    return await handleDelete(req, userId);
  } catch (_err) {
    return jsonResponse({
      code: "ERROR",
      message: "Terjadi gangguan saat memproses foto",
    });
  }
});
