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

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function sanitizeHeaderValue(value: string): string {
  return value.replace(/[\r\n]+/g, " ").trim();
}

function buildOtpEmailText(userName: string, otpCode: string, recipientEmail: string): string {
  return [
    "CloBle",
    "",
    `Halo ${userName},`,
    "",
    "Kami menerima permintaan pengaturan ulang password untuk akun CloBle Anda. Masukkan kode 6 digit berikut di aplikasi CloBle:",
    "",
    otpCode,
    "",
    "Kode ini berlaku selama 10 menit dan otomatis hangus setelah 5 kali salah percobaan.",
    "",
    "Jika Anda tidak meminta pengaturan ulang password, abaikan email ini. Password akun Anda tidak akan berubah.",
    "",
    `Email ini dikirim untuk akun ${recipientEmail} di aplikasi CloBle.`,
  ].join("\n");
}

function buildOtpEmailHtml(userName: string, otpCode: string, recipientEmail: string): string {
  const safeName = escapeHtml(userName);
  const safeEmail = escapeHtml(recipientEmail);
  const spacedOtp = escapeHtml(otpCode.split("").join(" "));

  return `<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Kode verifikasi akun CloBle</title>
</head>
<body style="margin:0;padding:0;background-color:#F8FAFD;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#111827;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#F8FAFD;padding:32px 16px;">
    <tr>
      <td align="center">
        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="max-width:520px;background-color:#FFFFFF;border:1px solid #D8E2F0;border-radius:12px;">
          <tr>
            <td style="padding:28px 28px 20px 28px;border-bottom:1px solid #D8E2F0;">
              <p style="margin:0;font-size:20px;line-height:26px;font-weight:700;color:#2554E6;">CloBle</p>
            </td>
          </tr>
          <tr>
            <td style="padding:24px 28px;">
              <p style="margin:0 0 12px 0;font-size:16px;line-height:24px;font-weight:600;color:#111827;">Halo ${safeName},</p>
              <p style="margin:0 0 20px 0;font-size:14px;line-height:22px;color:#475569;">Kami menerima permintaan pengaturan ulang password untuk akun CloBle Anda. Masukkan kode 6 digit berikut di aplikasi CloBle:</p>
              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 20px 0;">
                <tr>
                  <td align="center" style="background-color:#EAF4FF;border:1px solid #D8E2F0;border-radius:10px;padding:18px 16px;">
                    <span style="font-size:28px;line-height:34px;font-weight:700;letter-spacing:4px;color:#1D4ED8;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;">${spacedOtp}</span>
                  </td>
                </tr>
              </table>
              <p style="margin:0 0 12px 0;font-size:14px;line-height:22px;color:#475569;">Kode ini berlaku selama 10 menit dan otomatis hangus setelah 5 kali salah percobaan.</p>
              <p style="margin:0;font-size:14px;line-height:22px;color:#475569;">Jika Anda tidak meminta pengaturan ulang password, abaikan email ini. Password akun Anda tidak akan berubah.</p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 28px 24px 28px;border-top:1px solid #D8E2F0;">
              <p style="margin:0;font-size:12px;line-height:18px;color:#475569;">Email ini dikirim untuk akun ${safeEmail} di aplikasi CloBle.</p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`;
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
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!cleanEmail || !emailRegex.test(cleanEmail)) {
      return jsonResponse({ code: "VALIDATION_FAILED", message: "Format email tidak valid" });
    }

    const gmailUser = (Deno.env.get("GMAIL_USER") ?? "").trim();
    const gmailAppPassword = (Deno.env.get("GMAIL_APP_PASSWORD") ?? "").replace(/\s+/g, "");
    if (!gmailUser || !gmailAppPassword) {
      return jsonResponse({
        code: "SMTP_NOT_CONFIGURED",
        message: "Layanan email OTP sedang tidak tersedia. Silakan coba lagi nanti.",
      });
    }

    const otpCode = generateSixDigitOtp();

    const { data: issueResult, error: issueError } = await supabase.rpc("rpc_issue_password_reset_otp", {
      p_email: cleanEmail,
      p_otp_code: otpCode,
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
      const userName = sanitizeHeaderValue(String(issueResult?.userName ?? "")) || "Pengguna CloBle";
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
        from: `CloBle <${gmailUser}>`,
        replyTo: gmailUser,
        to: cleanEmail,
        subject: `Kode verifikasi akun CloBle untuk ${userName}`,
        text: buildOtpEmailText(userName, otpCode, cleanEmail),
        html: buildOtpEmailHtml(userName, otpCode, cleanEmail),
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
