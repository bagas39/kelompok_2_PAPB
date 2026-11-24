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

const DEFAULT_ITERATIONS = 120000;
const MIN_ITERATIONS = 10000;
const MAX_ITERATIONS = 5000000;
const SALT_LENGTH_BYTES = 16;
const DK_LEN_BYTES = 32;
const ALGORITHM_NAME = "pbkdf2_hmac_sha256";
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const SESSION_TOKEN_REGEX = /^[\x21-\x7E]{10,200}$/;
const LOGIN_WINDOW_MS = 900000;
const MAX_LOGIN_ATTEMPTS = 5;

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

function hexToBytes(hex: string): Uint8Array | null {
  if (!hex || hex.length % 2 !== 0 || !/^[0-9a-fA-F]+$/.test(hex)) {
    return null;
  }
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes;
}

function bytesToHex(bytes: Uint8Array): string {
  const digits = "0123456789abcdef";
  let out = "";
  for (let i = 0; i < bytes.length; i++) {
    const b = bytes[i];
    out += digits[b >>> 4] + digits[b & 0x0f];
  }
  return out;
}

async function derivePbkdf2Sha256(
  password: string,
  salt: Uint8Array,
  iterations: number,
  dkLenBytes = DK_LEN_BYTES,
): Promise<Uint8Array> {
  const passwordBytes = new TextEncoder().encode(password);
  const key = await crypto.subtle.importKey(
    "raw",
    passwordBytes,
    { name: "PBKDF2" },
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt,
      iterations,
      hash: "SHA-256",
    },
    key,
    dkLenBytes * 8,
  );
  return new Uint8Array(bits);
}

async function hashWithSaltHex(
  password: string,
  saltHex: string,
  iterations: number,
): Promise<string | null> {
  if (!Number.isInteger(iterations) || iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
    return null;
  }
  const salt = hexToBytes(saltHex);
  if (!salt || salt.length < SALT_LENGTH_BYTES) {
    return null;
  }
  const derived = await derivePbkdf2Sha256(password, salt, iterations, DK_LEN_BYTES);
  return `${ALGORITHM_NAME}$${iterations}$${saltHex.toLowerCase()}$${bytesToHex(derived)}`;
}

async function hashNewPassword(
  password: string,
  iterations = DEFAULT_ITERATIONS,
): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_LENGTH_BYTES));
  const derived = await derivePbkdf2Sha256(password, salt, iterations, DK_LEN_BYTES);
  return `${ALGORITHM_NAME}$${iterations}$${bytesToHex(salt)}$${bytesToHex(derived)}`;
}

function isValidEmail(email: string): boolean {
  return email.length > 0 && email.length <= 254 && EMAIL_REGEX.test(email);
}

function validateNewPassword(password: string): string | null {
  if (password.length < 8) {
    return "Password minimal 8 karakter";
  }
  if (password.length > 128) {
    return "Password maksimal 128 karakter";
  }
  const hasLetter = /\p{L}/u.test(password);
  const hasDigit = /\p{N}/u.test(password);
  if (!hasLetter || !hasDigit) {
    return "Password harus mengandung huruf dan angka";
  }
  return null;
}

async function handleLogin(payload: Record<string, unknown>): Promise<Response> {
  const cleanEmail = String(payload?.email ?? "").trim().toLowerCase();
  const rawPassword = typeof payload?.password === "string" ? payload.password : "";

  if (!cleanEmail || cleanEmail.length > 254 || !rawPassword || rawPassword.length > 256) {
    return jsonResponse({ code: "INVALID_CREDENTIALS" });
  }

  const nowMs = Date.now();
  const windowStartMs = nowMs - LOGIN_WINDOW_MS;
  const { data: attempts, error: attemptError } = await supabase
    .from("login_attempts")
    .select("attemptedAt")
    .eq("email", cleanEmail)
    .gte("attemptedAt", windowStartMs)
    .order("attemptedAt", { ascending: true });

  if (!attemptError && Array.isArray(attempts) && attempts.length >= MAX_LOGIN_ATTEMPTS) {
    const oldestAttempt = Number(attempts[0]?.attemptedAt ?? nowMs);
    const retryAfterSeconds = Math.max(1, Math.ceil(((oldestAttempt + LOGIN_WINDOW_MS) - nowMs) / 1000));
    const retryMinutes = Math.max(1, Math.ceil(retryAfterSeconds / 60));
    return jsonResponse({
      code: "RATE_LIMITED",
      retryAfterSeconds,
      message: `Terlalu banyak percobaan login gagal. Coba lagi dalam ${retryMinutes} menit.`,
    });
  }

  const { data: challengeResp, error: challengeError } = await supabase.rpc("rpc_get_login_challenge", {
    p_email: cleanEmail,
  });

  if (challengeError || !challengeResp || challengeResp.code !== "SUCCESS") {
    return jsonResponse({ code: "ERROR", message: "Gagal memproses autentikasi login" });
  }

  const iterations = Number(challengeResp.iterations ?? DEFAULT_ITERATIONS);
  const saltHex = String(challengeResp.saltHex ?? "");
  const computedHash = (await hashWithSaltHex(rawPassword, saltHex, iterations)) ?? "invalid_hash";

  const { data: sessionResp, error: sessionError } = await supabase.rpc("rpc_create_session", {
    p_email: cleanEmail,
    p_password_hash: computedHash,
  });

  if (sessionError || !sessionResp) {
    return jsonResponse({ code: "ERROR", message: "Gagal membuat sesi login" });
  }

  return jsonResponse(sessionResp as Record<string, unknown>);
}

async function handleRegister(payload: Record<string, unknown>): Promise<Response> {
  const cleanName = String(payload?.name ?? "").trim();
  const cleanEmail = String(payload?.email ?? "").trim().toLowerCase();
  const rawPassword = typeof payload?.password === "string" ? payload.password : "";
  const cleanCity = String(payload?.city ?? "").trim();
  const latitude = Number(payload?.latitude);
  const longitude = Number(payload?.longitude);

  if (!cleanName || cleanName.length > 100) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: "Nama tidak valid" });
  }
  if (!isValidEmail(cleanEmail)) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: "Format email tidak valid" });
  }
  if (cleanEmail.endsWith("@cloble.demo")) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: "Email domain @cloble.demo tidak dapat didaftarkan" });
  }
  if (!cleanCity || cleanCity.length > 120) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: "Lokasi domisili wajib dipilih" });
  }
  if (
    !Number.isFinite(latitude) ||
    !Number.isFinite(longitude) ||
    latitude < -90 ||
    latitude > 90 ||
    longitude < -180 ||
    longitude > 180
  ) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: "Koordinat lokasi tidak valid" });
  }

  const passwordErr = validateNewPassword(rawPassword);
  if (passwordErr) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: passwordErr });
  }

  const passwordHash = await hashNewPassword(rawPassword);
  const { data: regResp, error: regError } = await supabase.rpc("rpc_register_user", {
    p_name: cleanName,
    p_email: cleanEmail,
    p_password_hash: passwordHash,
    p_city: cleanCity,
    p_latitude: latitude,
    p_longitude: longitude,
  });

  if (regError || !regResp) {
    return jsonResponse({ code: "ERROR", message: "Gagal registrasi" });
  }

  return jsonResponse(regResp as Record<string, unknown>);
}

async function handleChangePassword(
  req: Request,
  payload: Record<string, unknown>,
): Promise<Response> {
  const sessionToken = (req.headers.get("x-session-token") ?? "").trim();
  if (!SESSION_TOKEN_REGEX.test(sessionToken)) {
    return jsonResponse({ code: "UNAUTHORIZED" });
  }

  const currentPassword = typeof payload?.currentPassword === "string" ? payload.currentPassword : "";
  const newPassword = typeof payload?.newPassword === "string" ? payload.newPassword : "";

  if (!currentPassword || currentPassword.length > 256) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: "Password saat ini wajib diisi" });
  }
  const passwordErr = validateNewPassword(newPassword);
  if (passwordErr) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: passwordErr });
  }
  if (currentPassword === newPassword) {
    return jsonResponse({
      code: "VALIDATION_FAILED",
      message: "Password baru tidak boleh sama dengan password saat ini",
    });
  }

  const sessionClient = createClient(supabaseUrl, supabaseServiceKey, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
    },
    global: {
      headers: {
        "x-session-token": sessionToken,
      },
    },
  });

  const { data: challengeResp, error: challengeError } = await sessionClient.rpc(
    "rpc_get_current_password_challenge",
    {},
  );
  if (challengeError || !challengeResp || challengeResp.code !== "SUCCESS") {
    return jsonResponse({ code: "UNAUTHORIZED" });
  }

  const iterations = Number(challengeResp.iterations ?? DEFAULT_ITERATIONS);
  const saltHex = String(challengeResp.saltHex ?? "");
  if (!saltHex) {
    return jsonResponse({ code: "UNAUTHORIZED" });
  }

  const currentHash = await hashWithSaltHex(currentPassword, saltHex, iterations);
  if (!currentHash) {
    return jsonResponse({ code: "INVALID_CURRENT_PASSWORD" });
  }
  const newHash = await hashNewPassword(newPassword);

  const { data: changeResp, error: changeError } = await sessionClient.rpc(
    "rpc_change_password_authenticated",
    {
      p_current_password_hash: currentHash,
      p_new_password_hash: newHash,
    },
  );

  if (changeError || !changeResp) {
    return jsonResponse({ code: "ERROR", message: "Gagal memperbarui password" });
  }

  return jsonResponse(changeResp as Record<string, unknown>);
}

async function handleVerifyResetOtp(payload: Record<string, unknown>): Promise<Response> {
  const cleanEmail = String(payload?.email ?? "").trim().toLowerCase();
  const cleanOtp = String(payload?.otpCode ?? "").trim();
  const newPassword = typeof payload?.newPassword === "string" ? payload.newPassword : "";

  if (!isValidEmail(cleanEmail)) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: "Format email tidak valid" });
  }
  if (!/^[0-9]{6}$/.test(cleanOtp)) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: "Kode OTP harus 6 digit angka" });
  }
  const passwordErr = validateNewPassword(newPassword);
  if (passwordErr) {
    return jsonResponse({ code: "VALIDATION_FAILED", message: passwordErr });
  }

  const newHash = await hashNewPassword(newPassword);
  const { data: resetResp, error: resetError } = await supabase.rpc(
    "rpc_verify_otp_and_reset_password",
    {
      p_email: cleanEmail,
      p_otp_code: cleanOtp,
      p_new_password_hash: newHash,
    },
  );

  if (resetError || !resetResp) {
    return jsonResponse({ code: "ERROR", message: "Gagal mereset password" });
  }

  return jsonResponse(resetResp as Record<string, unknown>);
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
    const payload = (await req.json()) as Record<string, unknown>;
    const action = String(payload?.action ?? "").trim();

    switch (action) {
      case "login":
        return await handleLogin(payload);
      case "register":
        return await handleRegister(payload);
      case "change-password":
        return await handleChangePassword(req, payload);
      case "verify-reset-otp":
        return await handleVerifyResetOtp(payload);
      default:
        return jsonResponse({ code: "VALIDATION_FAILED", message: "Aksi autentikasi tidak dikenal" });
    }
  } catch (_err) {
    return jsonResponse({ code: "INVALID_REQUEST", message: "Permintaan autentikasi tidak valid" });
  }
});
