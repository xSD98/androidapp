/* eslint-disable indent, max-len, object-curly-spacing, comma-dangle, eol-last */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * Trigger: nuovo messaggio su chats/{chatId}/messages/{msgId}
 * Invia una data-notification ai token FCM del destinatario.
 */
exports.onChatMessage = onDocumentCreated(
  { document: "chats/{chatId}/messages/{msgId}", region: "us-central1" },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const m = snap.data() || {};
    const toId = m.toId;
    const fromId = m.fromId;

    // niente destinatario oppure messaggio a se stessi → ignora
    if (!toId || fromId === toId) return;

    const db = getFirestore();
    const userRef = db.collection("users").doc(toId);
    const userDoc = await userRef.get();
    if (!userDoc.exists) return;

    const u = userDoc.data() || {};
    let tokens = [];

    // compatibilità con vecchi campi
    if (Array.isArray(u.fcmTokens)) tokens = u.fcmTokens;
    if (!tokens.length && u.fcmToken) tokens = [u.fcmToken];
    if (!tokens.length) {
      const tSnap = await userRef.collection("fcmTokens").get();
      tokens = tSnap.docs.map((d) => d.id);
    }

    tokens = tokens.filter(Boolean);
    if (!tokens.length) return;

    const body = (m.text && m.text.length) ? m.text : "Hai ricevuto un messaggio";

    // payload: anche in data per il foreground
    const payload = {
      tokens,
      notification: {
        title: "Nuovo messaggio",
        body,
      },
      data: {
        title: "Nuovo messaggio",
        body,
        chatId: event.params.chatId || "",
        msgId: event.params.msgId || "",
        fromId: String(fromId || ""),
        toId: String(toId || ""),
        withId: String(fromId || ""), // usato dal client per aprire la chat col mittente
      },
      android: { priority: "high", notification: { channelId: "chat_messages" } },
      apns: { headers: { "apns-priority": "10" } },
    };

    const res = await getMessaging().sendEachForMulticast(payload);

    // rimozione token non validi
    const invalid = [];
    res.responses.forEach((r, i) => {
      if (!r.success) {
        const code = r.error && r.error.code;
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-registration-token"
        ) {
          invalid.push(tokens[i]);
        }
      }
    });

    if (invalid.length) {
      const batch = db.batch();
      invalid.forEach((t) => {
        batch.delete(userRef.collection("fcmTokens").doc(t));
      });
      await batch.commit();
    }
  }
);