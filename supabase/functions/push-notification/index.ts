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

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    const payload = await req.json();
    const { table, record } = payload;

    if (!record) {
      return new Response(JSON.stringify({ message: "No record provided" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    let targetUserIds: number[] = [];
    let title = "CloBle";
    let body = "";
    let destination = "notifications";
    let matchId: string | null = null;

    if (table === "chat_messages") {
      const currentMatchId = record.matchId;
      matchId = String(currentMatchId);
      destination = "chat";

      const { data: matchData } = await supabase
        .from("matches")
        .select("user1Id, user2Id")
        .eq("id", currentMatchId)
        .single();

      if (matchData) {
        const recipientId = record.senderId === matchData.user1Id ? matchData.user2Id : matchData.user1Id;
        targetUserIds.push(recipientId);
      }

      const { data: senderData } = await supabase
        .from("users")
        .select("name")
        .eq("id", record.senderId)
        .single();

      const senderName = senderData?.name || "Pengguna CloBle";
      title = senderName;
      body = record.messageText || "Mengirim pesan baru";
    } else if (table === "notifications") {
      targetUserIds.push(record.userId);
      title = record.title || "Notifikasi CloBle";
      body = record.body || "";
      destination = "notifications";
      if (record.relatedMatchId) {
        matchId = String(record.relatedMatchId);
      }
    }

    if (targetUserIds.length === 0) {
      return new Response(JSON.stringify({ success: true, sent: 0, reason: "No target users" }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    const { data: tokenRows } = await supabase
      .from("user_fcm_tokens")
      .select("fcmToken")
      .in("userId", targetUserIds);

    const tokens = (tokenRows || []).map((r) => r.fcmToken).filter(Boolean);
    if (tokens.length === 0) {
      return new Response(JSON.stringify({ success: true, sent: 0, reason: "No FCM tokens found" }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    const messagePayload = {
      tokens: tokens,
      notification: {
        title: title,
        body: body,
      },
      data: {
        title: title,
        body: body,
        destination: destination,
        ...(matchId ? { matchId } : {}),
      },
      android: {
        priority: "high" as const,
        notification: {
          channelId: "cloble_barter_notifications",
          sound: "default",
        },
      },
    };

    const response = await admin.messaging().sendEachForMulticast(messagePayload);

    return new Response(
      JSON.stringify({
        success: true,
        successCount: response.successCount,
        failureCount: response.failureCount,
      }),
      { headers: { "Content-Type": "application/json" } }
    );
  } catch (error: any) {
    console.error("Error processing push webhook:", error);
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
