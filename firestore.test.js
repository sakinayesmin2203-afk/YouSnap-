const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const ADMIN_UID = "admin_uid_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write posts/reels/stories", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("posts").get());
  await assertFails(unauthDb.collection("reels").get());
  await assertFails(unauthDb.collection("stories").get());
});

test("Regular user can create normal post/reel/story without viral fields, but cannot self-set viral fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 1000);

  // Normal post succeeds
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_alice_1").set({
      id: "1001",
      userId: ALICE_UID,
      creatorUid: ALICE_UID,
      username: "alice",
      timestamp: 1001,
      caption: "Hello world",
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      likedBy: [],
      createdAt: created,
    })
  );

  // Tampering with isViral on create fails
  await assertFails(
    aliceDb.collection("posts").doc("post_alice_tamper").set({
      id: "1002",
      userId: ALICE_UID,
      creatorUid: ALICE_UID,
      username: "alice",
      timestamp: 1002,
      isViral: true,
      viralDurationDays: 7,
      viralDurationHours: 0,
      viralExpiresAt: "2027-01-01T00:00:00.000Z",
      createdAt: created,
    })
  );

  // Tampering with viral fields on reel create fails
  await assertFails(
    aliceDb.collection("reels").doc("reel_alice_tamper").set({
      id: "2001",
      userId: ALICE_UID,
      creatorUid: ALICE_UID,
      username: "alice",
      timestamp: 2001,
      isViral: true,
      createdAt: created,
    })
  );

  // Tampering with viral fields on story create fails
  await assertFails(
    aliceDb.collection("stories").doc("alice").set({
      storyId: "3001",
      userId: ALICE_UID,
      creatorUid: ALICE_UID,
      username: "alice",
      timestamp: 3001,
      viralExpiresAt: "2027-01-01T00:00:00.000Z",
      createdAt: created,
    })
  );
});

test("Regular user (owner or viewer) cannot update viral fields, while admin can boost and users can read/engage", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ADMIN_UID).set({
      username: "admin_user",
      isAdmin: true,
    });
    await db.collection("posts").doc("post_1").set({
      id: "1001",
      userId: ALICE_UID,
      creatorUid: ALICE_UID,
      username: "alice",
      timestamp: 1001,
      caption: "Initial caption",
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      likedBy: [],
    });
    await db.collection("reels").doc("reel_1").set({
      id: "2001",
      userId: ALICE_UID,
      creatorUid: ALICE_UID,
      username: "alice",
      timestamp: 2001,
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      viewsCount: 0,
      likedBy: [],
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  // Owner Alice CANNOT set viral fields on her own post or reel
  await assertFails(
    aliceDb.collection("posts").doc("post_1").update({
      isViral: true,
      viralDurationDays: 3,
      viralDurationHours: 0,
      viralExpiresAt: "2027-01-01T00:00:00.000Z",
    })
  );
  await assertFails(
    aliceDb.collection("reels").doc("reel_1").update({
      viralDurationHours: 12,
    })
  );

  // Other user Bob CANNOT set viral fields
  await assertFails(
    bobDb.collection("reels").doc("reel_1").update({
      isViral: true,
    })
  );

  // Admin CAN start viral boost
  await assertSucceeds(
    adminDb.collection("reels").doc("reel_1").update({
      isViral: true,
      viralDurationDays: 2,
      viralDurationHours: 6,
      viralExpiresAt: "2027-01-01T00:00:00.000Z",
    })
  );

  // Signed-in user Bob CAN read the reel including viral fields for ranking
  const snap = await assertSucceeds(bobDb.collection("reels").doc("reel_1").get());
  if (snap.data().isViral !== true) {
    throw new Error("Expected isViral === true on read");
  }

  // Signed-in user Bob CAN still increment views/likes on an active viral reel
  await assertSucceeds(
    bobDb.collection("reels").doc("reel_1").update({
      viewsCount: 1,
      likesCount: 1,
      likedBy: [BOB_UID],
    })
  );

  // Owner Alice CAN edit her caption without touching viral fields, but CANNOT alter or turn off viral fields
  await assertSucceeds(
    aliceDb.collection("reels").doc("reel_1").update({
      caption: "Updated caption",
    })
  );
  await assertFails(
    aliceDb.collection("reels").doc("reel_1").update({
      isViral: false,
    })
  );
});
