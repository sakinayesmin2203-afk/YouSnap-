const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Triggers whenever a Follow, Like, or Comment document is written to Firestore
 * `notifications/{notifId}` and pushes a real-time FCM notification to the recipient's device.
 */
exports.sendSocialNotification = onDocumentCreated("notifications/{notifId}", async (event) => {
  const snap = event.data;
  if (!snap) return;

  const notif = snap.data();
  const recipientUid = (notif.recipientUid || "").trim();
  const recipientUsername = (notif.recipientUsername || "").trim();
  const senderUid = (notif.senderUid || "").trim();
  const senderUsername = (notif.senderUsername || "user").trim();

  if (senderUid && recipientUid && senderUid === recipientUid) return;

  let targetToken = "";
  const db = admin.firestore();

  if (recipientUid) {
    const userDoc = await db.collection("users").doc(recipientUid).get();
    if (userDoc.exists) {
      targetToken = userDoc.data().fcmToken || "";
    }
  }

  if (!targetToken && recipientUsername) {
    const byUsername = await db
      .collection("users")
      .where("username", "==", recipientUsername)
      .limit(1)
      .get();
    if (!byUsername.empty) {
      targetToken = byUsername.docs[0].data().fcmToken || "";
    }
  }

  if (!targetToken) return;

  const type = (notif.type || "activity").toLowerCase();
  const title =
    type === "follow"
      ? "New Follower"
      : type === "like"
      ? "New Like"
      : type === "comment"
      ? "New Comment"
      : "YouSnap";

  const detail = notif.detail || "interacted with you on YouSnap.";
  const body = `@${senderUsername} ${detail}`;

  const message = {
    token: targetToken,
    notification: {
      title,
      body,
    },
    data: {
      notificationId: event.params.notifId,
      type,
      senderUsername,
      postId: String(notif.postId || ""),
      recipientUid,
    },
    android: {
      priority: "high",
      notification: {
        channelId: "yousnap_social_activity",
        icon: "ic_notification",
      },
    },
  };

  try {
    await admin.messaging().send(message);
  } catch (err) {
    console.error("Failed to send FCM push notification:", err);
  }
});
