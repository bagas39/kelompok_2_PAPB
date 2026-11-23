import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";
import admin from "npm:firebase-admin@12.7.0";

const serviceAccountJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
if (serviceAccountJson && !admin.apps.length) {
  try {
    const serviceAccount = JSON.parse(serviceAccountJson);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
    });
  } catch (err) {
    console.error("Failed to initialize Firebase Admin:", err);
  }
}

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const supabase = createClient(supabaseUrl, supabaseServiceKey);

const MAX_EVENT_AGE_MS = 5 * 60 * 1000;
const jsonHeaders = { "Content-Type": "application/json" };

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    const expectedSecret = (Deno.env.get("PUSH_WEBHOOK_SECRET") ?? "").trim();
    if (!expectedSecret) {
      return new Response(JSON.stringify({ error: "Push webhook secret is not configured" }), {
        status: 500,
        headers: jsonHeaders,
      });
    }
    const providedSecret = req.headers.get("x-webhook-secret");
    if (providedSecret !== expectedSecret) {
      return new Response(JSON.stringify({ error: "Unauthorized webhook caller" }), {
        status: 401,
        headers: jsonHeaders,
      });
    }

    const payload = await req.json();
    const table = payload?.table;
    const record = payload?.record;

    if (table !== "chat_messages" && table !== "notifications") {
      return new Response(JSON.stringify({ error: "Unsupported table" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }

    const recordId = Number(record?.id);
    if (!Number.isInteger(recordId) || recordId <= 0) {
      return new Response(JSON.stringify({ error: "Invalid record id" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }

    let targetUserIds: number[] = [];
    let title = "CloBle";
    let body = "";
    let destination = "notifications";
    let matchId: string | null = null;
    let eventId = String(recordId);
    let targetUserId = "";

    if (table === "chat_messages") {
      const { data: dbMsg, error: msgError } = await supabase
        .from("chat_messages")
        .select("id, matchId, senderId, messageText, sentAt")
        .eq("id", recordId)
        .maybeSingle();

      if (msgError || !dbMsg) {
        return new Response(JSON.stringify({ error: "Record not found" }), {
          status: 404,
          headers: jsonHeaders,
        });
      }

      if (Math.abs(Date.now() - Number(dbMsg.sentAt)) > MAX_EVENT_AGE_MS) {
        return new Response(JSON.stringify({ success: true, sent: 0, reason: "Stale event ignored" }), {
          headers: jsonHeaders,
        });
      }

      if (
        (record?.matchId != null && Number(record.matchId) !== Number(dbMsg.matchId)) ||
        (record?.senderId != null && Number(record.senderId) !== Number(dbMsg.senderId))
      ) {
        return new Response(JSON.stringify({ error: "Payload mismatch" }), {
          status: 403,
          headers: jsonHeaders,
        });
      }

      const { data: matchData, error: matchError } = await supabase
        .from("matches")
        .select("user1Id, user2Id")
        .eq("id", dbMsg.matchId)
        .maybeSingle();

      if (matchError || !matchData) {
        return new Response(JSON.stringify({ error: "Match not found" }), {
          status: 404,
          headers: jsonHeaders,
        });
      }

      if (
        Number(dbMsg.senderId) !== Number(matchData.user1Id) &&
        Number(dbMsg.senderId) !== Number(matchData.user2Id)
      ) {
        return new Response(JSON.stringify({ error: "Sender is not a match participant" }), {
          status: 403,
          headers: jsonHeaders,
        });
      }

      const recipientId =
        Number(dbMsg.senderId) === Number(matchData.user1Id)
          ? Number(matchData.user2Id)
          : Number(matchData.user1Id);

      const { data: senderData } = await supabase
        .from("users")
        .select("name")
        .eq("id", dbMsg.senderId)
        .maybeSingle();

      targetUserIds = [recipientId];
      targetUserId = String(recipientId);
      eventId = String(dbMsg.id);
      matchId = String(dbMsg.matchId);
      destination = "chat";
      title = senderData?.name || "Pengguna CloBle";
      body = dbMsg.messageText || "Mengirim pesan baru";
    } else if (table === "notifications") {
      const { data: dbNotif, error: notifError } = await supabase
        .from("notifications")
        .select("id, userId, title, body, relatedMatchId, isRead, createdAt")
        .eq("id", recordId)
        .maybeSingle();

      if (notifError || !dbNotif) {
        return new Response(JSON.stringify({ error: "Record not found" }), {
          status: 404,
          headers: jsonHeaders,
        });
      }

      if (dbNotif.isRead === true) {
        return new Response(JSON.stringify({ success: true, sent: 0, reason: "Notification already read" }), {
          headers: jsonHeaders,
        });
      }

      if (Math.abs(Date.now() - Number(dbNotif.createdAt)) > MAX_EVENT_AGE_MS) {
        return new Response(JSON.stringify({ success: true, sent: 0, reason: "Stale event ignored" }), {
          headers: jsonHeaders,
        });
      }

      if (record?.userId != null && Number(record.userId) !== Number(dbNotif.userId)) {
        return new Response(JSON.stringify({ error: "Payload mismatch" }), {
          status: 403,
          headers: jsonHeaders,
        });
      }

      targetUserIds = [Number(dbNotif.userId)];
      targetUserId = String(dbNotif.userId);
      eventId = String(dbNotif.id);
      title = dbNotif.title || "Notifikasi CloBle";
      body = dbNotif.body || "";
      destination = "notifications";
      if (dbNotif.relatedMatchId) {
        matchId = String(dbNotif.relatedMatchId);
      }
    }

    if (targetUserIds.length === 0) {
      return new Response(JSON.stringify({ success: true, sent: 0, reason: "No target users" }), {
        headers: jsonHeaders,
      });
    }

    if (!admin.apps.length) {
      return new Response(JSON.stringify({ error: "Firebase Admin is not configured" }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    const { data: sessionRows, error: sessionError } = await supabase
      .from("user_sessions")
      .select("userId")
      .in("userId", targetUserIds)
      .gt("expiresAt", Date.now());

    if (sessionError) {
      return new Response(JSON.stringify({ error: "Failed to verify active sessions" }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    const activeTargetUserIds = Array.from(
      new Set(
        (sessionRows || [])
          .map((row) => Number(row.userId))
          .filter((id) => Number.isInteger(id) && id > 0)
      )
    );

    if (activeTargetUserIds.length === 0) {
      return new Response(JSON.stringify({ success: true, sent: 0, reason: "No active sessions" }), {
        headers: jsonHeaders,
      });
    }

    const { data: tokenRows, error: tokenError } = await supabase
      .from("user_fcm_tokens")
      .select("fcmToken")
      .in("userId", activeTargetUserIds);

    if (tokenError) {
      return new Response(JSON.stringify({ error: "Failed to fetch FCM tokens" }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    const tokens = Array.from(
      new Set(
        (tokenRows || [])
          .map((r) => (typeof r.fcmToken === "string" ? r.fcmToken.trim() : ""))
          .filter((token) => token.length > 0)
      )
    );
    if (tokens.length === 0) {
      return new Response(JSON.stringify({ success: true, sent: 0, reason: "No FCM tokens found" }), {
        headers: jsonHeaders,
      });
    }

    const MAX_MULTICAST_TOKENS = 500;
    const dataPayload: Record<string, string> = {
      id: eventId,
      userId: targetUserId,
      title: title,
      body: body,
      destination: destination,
      ...(table === "notifications" ? { notificationId: eventId } : {}),
      ...(matchId ? { matchId } : {}),
    };

    let successCount = 0;
    let failureCount = 0;
    const invalidTokens: string[] = [];

    for (let offset = 0; offset < tokens.length; offset += MAX_MULTICAST_TOKENS) {
      const tokenChunk = tokens.slice(offset, offset + MAX_MULTICAST_TOKENS);
      const response = await admin.messaging().sendEachForMulticast({
        tokens: tokenChunk,
        data: dataPayload,
        android: {
          priority: "high" as const,
        },
      });
      successCount += response.successCount;
      failureCount += response.failureCount;
      response.responses.forEach((res, idx) => {
        const errCode = res.error?.code;
        if (
          !res.success &&
          (errCode === "messaging/registration-token-not-registered" ||
            errCode === "messaging/invalid-registration-token")
        ) {
          const badToken = tokenChunk[idx];
          if (badToken) {
            invalidTokens.push(badToken);
          }
        }
      });
    }

    if (invalidTokens.length > 0) {
      await supabase.from("user_fcm_tokens").delete().in("fcmToken", invalidTokens);
    }

    return new Response(
      JSON.stringify({
        success: true,
        successCount: successCount,
        failureCount: failureCount,
      }),
      { headers: jsonHeaders }
    );
  } catch (error) {
    console.error("Error processing push webhook:", error);
    return new Response(JSON.stringify({ error: "Internal server error" }), {
      status: 500,
      headers: jsonHeaders,
    });
  }
});
