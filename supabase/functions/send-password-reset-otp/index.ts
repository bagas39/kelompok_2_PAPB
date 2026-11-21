import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";
import nodemailer from "npm:nodemailer@6.9.16";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const supabase = createClient(supabaseUrl, supabaseServiceKey);

function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-session-token",
    },
  });
}

function generateSixDigitOtp(): string {
  const maxUnbiased = 4294800000;
  const buf = new Uint32Array(1);
  while (true) {
    crypto.getRandomValues(buf);
    if (buf[0] < maxUnbiased) {
      return String(100000 + (buf[0] % 900000));
    }
  }
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-session-token",
      },
    });
  }

  if (req.method !== "POST") {
    return jsonResponse({ code: "METHOD_NOT_ALLOWED", message: "Gunakan metode POST" }, 405);
  }

  try {
    const payload = await req.json();
    const cleanEmail = String(payload?.email ?? "").trim().toLowerCase();
    if (!cleanEmail || !cleanEmail.includes("@")) {
      return jsonResponse({ code: "VALIDATION_FAILED", message: "Format email tidak valid" });
    }

    const gmailUser = (Deno.env.get("GMAIL_USER") ?? "").trim();
    const gmailAppPassword = (Deno.env.get("GMAIL_APP_PASSWORD") ?? "").replace(/\s+/g, "");
    if (!gmailUser || !gmailAppPassword) {
      return jsonResponse({
        code: "SMTP_NOT_CONFIGURED",
        message: "Pengiriman email OTP Gmail SMTP belum dikonfigurasi di Supabase Secrets (GMAIL_USER / GMAIL_APP_PASSWORD).",
      });
    }

    const otpCode = generateSixDigitOtp();
    const nowMillis = Date.now();

    const { data: issueResult, error: issueError } = await supabase.rpc("rpc_issue_password_reset_otp", {
      p_email: cleanEmail,
      p_otp_code: otpCode,
      p_now_millis: nowMillis,
    });

    if (issueError) {
      return jsonResponse({
        code: "ERROR",
        message: "Permintaan OTP tidak dapat diproses saat ini.",
      });
    }

    const issueCode = String(issueResult?.code ?? "ERROR");
    if (issueCode !== "SUCCESS") {
      const safeMessage =
        issueCode === "RATE_LIMITED"
          ? "Tunggu 30 detik sebelum meminta kode OTP baru."
          : "Permintaan OTP tidak dapat diproses.";
      return jsonResponse({
        code: issueCode,
        message: safeMessage,
      });
    }

    const shouldSendEmail = Boolean(issueResult?.shouldSendEmail);
    if (shouldSendEmail) {
      const userName = String(issueResult?.userName ?? "Pengguna CloBle");
      const transporter = nodemailer.createTransport({
        host: "smtp.gmail.com",
        port: 465,
        secure: true,
        auth: {
          user: gmailUser,
          pass: gmailAppPassword,
        },
      });

      await transporter.sendMail({
        from: `CloBle Security <${gmailUser}>`,
        to: cleanEmail,
        subject: `[CloBle] Kode OTP Reset Password: ${otpCode}`,
        text: [
          `Halo ${userName},`,
          "",
          "Kami menerima permintaan reset password untuk akun CloBle Anda.",
          "",
          `Kode OTP Anda: ${otpCode}`,
          "Kode ini berlaku selama 10 menit dan maksimal 5 kali percobaan.",
          "",
          "Jangan bagikan kode ini kepada siapa pun. Jika Anda tidak meminta reset password, abaikan email ini.",
          "",
          "Tim CloBle",
        ].join("\n"),
      });
    }

    return jsonResponse({
      code: "SUCCESS",
      message: "Kami akan mengirimkan OTP jika email Anda terdaftar.",
    });
  } catch (_err) {
    return jsonResponse({
      code: "SMTP_SEND_FAILED",
      message: "Terjadi gangguan saat memproses permintaan OTP. Silakan coba lagi.",
    });
  }
});
