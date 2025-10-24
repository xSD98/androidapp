/* eslint-disable indent, max-len */
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");

initializeApp();

/**
 * Trigger: nuovo messaggio su chats/{chatId}/messages/{msgId}
 * Invia una data-notification ai token FCM del destinatario.
 */
exports.onChatMessage = onDocumentCreated(
  "chats/{chatId}/messages/{msgId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const m = snap.data();

    const toId = m.toId;
    const fromId = m.fromId;
    if (!toId || fromId === toId) return;

    const db = getFirestore();
    const userRef = db.collection("users").doc(toId);
    const userDoc = await userRef.get();
    if (!userDoc.exists) return;

    const u = userDoc.data() || {};
    let tokens = [];

    if (Array.isArray(u.fcmTokens)) tokens = u.fcmTokens;
    if (!tokens.length && u.fcmToken) tokens = [u.fcmToken];
    if (!tokens.length) {
      const tSnap = await userRef.collection("fcmTokens").get();
      tokens = tSnap.docs.map((d) => d.id);
    }

    tokens = tokens.filter(Boolean);
    if (!tokens.length) return;

    const body = (m.text && m.text.length) ?
      m.text :
      "Hai ricevuto un messaggio";

    const message = {
      tokens,
      data: {
        title: "Nuovo messaggio",
        body,
        withId: fromId || "",
      },
      android: {priority: "high"},
      apns: {headers: {"apns-priority": "10"}},
    };

    const res = await getMessaging().sendEachForMulticast(message);

    // rimuovi token invalidi
    const invalid = [];
    res.responses.forEach((r, i) => {
      if (!r.success) {
        const code = r.error && r.error.code;
        if (code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token") {
          invalid.push(tokens[i]);
        }
      }
    });

    if (invalid.length) {
      const batch = db.batch();
      invalid.forEach((t) =>
        batch.delete(userRef.collection("fcmTokens").doc(t)));
      await batch.commit();
    }
  },
);
