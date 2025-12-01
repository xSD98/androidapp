/* eslint-env node */
/* eslint-disable max-len, valid-jsdoc */

const {onDocumentCreated, onDocumentUpdated} = require("firebase-functions/v2/firestore");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");

initializeApp();

/**
 * Ritorna i token FCM di un utente.
 * @param {string} userId
 * @return {Promise<{tokens: string[], userRef: import('firebase-admin/firestore').DocumentReference}>}
 */
async function tokensForUser(userId) {
  const db = getFirestore();
  const userRef = db.collection("users").doc(userId);
  const userDoc = await userRef.get();
  if (!userDoc.exists) return {tokens: [], userRef};

  const u = userDoc.data() || {};
  let tokens = [];
  if (Array.isArray(u.fcmTokens)) tokens = u.fcmTokens;
  if (!tokens.length && u.fcmToken) tokens = [u.fcmToken];
  if (!tokens.length) {
    const tSnap = await userRef.collection("fcmTokens").get();
    tokens = tSnap.docs.map((d) => d.id);
  }
  tokens = tokens.filter(Boolean);
  return {tokens, userRef};
}

/**
 * Rimuove i token FCM invalidi dalla subcollection dell’utente.
 * @param {import('firebase-admin/firestore').DocumentReference} userRef
 * @param {string[]} tokens
 * @param {import('firebase-admin/messaging').BatchResponse} res
 * @return {Promise<void>}
 */
async function purgeInvalidTokens(userRef, tokens, res) {
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
    const db = getFirestore();
    const batch = db.batch();
    invalid.forEach((t) => {
      batch.delete(userRef.collection("fcmTokens").doc(t));
    });
    await batch.commit();
  }
}

/** ================= Notifiche chat: nuovo messaggio ================= */
exports.onChatMessage = onDocumentCreated(
    {document: "chats/{chatId}/messages/{msgId}", region: "us-central1"},
    async (event) => {
      const snap = event.data;
      if (!snap) return;

      const m = snap.data() || {};
      const toId = m.toId;
      const fromId = m.fromId;
      if (!toId || fromId === toId) return;

      const {tokens, userRef} = await tokensForUser(toId);
      if (!tokens.length) return;

      const body = (m.text && m.text.length) ? m.text : "Hai ricevuto un messaggio";

      const payload = {
        tokens,
        notification: {title: "Nuovo messaggio", body},
        data: {
          title: "Nuovo messaggio",
          body,
          chatId: String(event.params.chatId || ""),
          msgId: String(event.params.msgId || ""),
          fromId: String(fromId || ""),
          toId: String(toId || ""),
          withId: String(fromId || ""),
          type: "chat_message",
        },
        android: {priority: "high", notification: {channelId: "chat_messages"}},
        apns: {headers: {"apns-priority": "10"}},
      };

      const res = await getMessaging().sendEachForMulticast(payload);
      await purgeInvalidTokens(userRef, tokens, res);
    },
);

/** ================= Notifica: caregiver avvia l’attività ================= */
exports.onActivityStarted = onDocumentUpdated(
    {document: "activities/{activityId}", region: "us-central1"},
    async (event) => {
      const before = event.data.before.data() || {};
      const after = event.data.after.data() || {};

      const beforeStatus = String(before.status || "");
      const afterStatus = String(after.status || "");
      const startedBefore = Boolean(before.startedAt);
      const startedAfter = Boolean(after.startedAt);

      const becameRunning =
      (beforeStatus !== "RUNNING" && afterStatus === "RUNNING") ||
      (!startedBefore && startedAfter);
      if (!becameRunning) return;

      const userId = after.userId;
      if (!userId) return;

      const {tokens, userRef} = await tokensForUser(userId);
      if (!tokens.length) return;

      const title = after.title || "Attività";
      const body = `${title} è stata avviata`;

      const payload = {
        tokens,
        notification: {title: "Attività avviata", body},
        data: {
          title: "Attività avviata",
          body,
          activityId: String(event.params.activityId || ""),
          route: `runner/${String(event.params.activityId || "")}`,
          type: "activity_started",
        },
        android: {priority: "high", notification: {channelId: "chat_messages"}},
        apns: {headers: {"apns-priority": "10"}},
      };

      const res = await getMessaging().sendEachForMulticast(payload);
      await purgeInvalidTokens(userRef, tokens, res);
    },
);

/** = Notifica: sotto-attività in ritardo (al caregiver) = */
exports.onSubtaskLate = onDocumentCreated(
    {document: "alerts/{alertId}", region: "us-central1"},
    async (event) => {
      const snap = event.data;
      if (!snap) return;

      const a = snap.data() || {};
      const caregiverId = a.caregiverId;
      const activityId = a.activityId;
      const subtaskId = a.subtaskId;
      const minutesExpected = Number(a.minutesExpected || 0);
      const minutesActual = Number(a.minutesActual || 0);
      if (!caregiverId) return;

      const db = getFirestore();
      let subTitle = "Sotto-attività";
      try {
        if (activityId && subtaskId) {
          const subDoc = await db
              .collection("activities").doc(activityId)
              .collection("subtasks").doc(subtaskId)
              .get();
          if (subDoc.exists) {
            subTitle = subDoc.get("title") || subTitle;
          }
        }
      } catch (e) {
        console.log("subtask lookup error", e && (e.message || e));
      }

      const delayMin = Math.max(0, minutesActual - minutesExpected);
      const body = delayMin > 0 ?
      `${subTitle} in ritardo di ${delayMin} min` :
      `${subTitle} terminata oltre il previsto`;

      const {tokens, userRef} = await tokensForUser(caregiverId);
      if (!tokens.length) return;

      const payload = {
        tokens,
        notification: {title: "Ritardo sotto-attività", body},
        data: {
          title: "Ritardo sotto-attività",
          body,
          activityId: String(activityId || ""),
          subtaskId: String(subtaskId || ""),
          minutesExpected: String(minutesExpected),
          minutesActual: String(minutesActual),
          type: "subtask_late",
          route: `runner/${String(activityId || "")}`,
        },
        android: {priority: "high", notification: {channelId: "chat_messages"}},
        apns: {headers: {"apns-priority": "10"}},
      };

      const res = await getMessaging().sendEachForMulticast(payload);
      await purgeInvalidTokens(userRef, tokens, res);
    },
);
