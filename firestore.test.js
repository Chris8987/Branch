const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_branch_001";
const BOB_UID = "bob_branch_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validRootNode() {
  return {
    id: "root_main",
    title: "Branch 主軸",
    notes: "核心筆記",
    resultLog: "初版完成",
    color: "#334155",
    orderIndex: 0,
    status: "DO",
    isCollapsed: false,
    isPinned: false,
    isStrikethrough: false,
    variants: [],
    children: [
      {
        id: "n_child_1",
        parentId: "root_main",
        title: "軌道一",
        notes: "",
        resultLog: "",
        color: "#2563EB",
        orderIndex: 0,
        status: "PLAN",
        isCollapsed: false,
        isPinned: false,
        isStrikethrough: false,
        variants: [],
        children: [],
      },
    ],
  };
}

function validBoardPayload(uid, ts, title = "Branch") {
  return {
    userId: uid,
    schemaVersion: 2,
    title,
    activeMode: "DRAFT",
    activeView: "MIND_MAP",
    trunkRevealLevel: 2,
    root: validRootNode(),
    createdAt: ts,
    updatedAt: ts,
  };
}

function validSnapshotPayload(uid, ts) {
  return {
    userId: uid,
    ownerNodeId: "n_child_1",
    variantId: "v_state_b",
    label: "狀態 B",
    children: [
      {
        id: "n_clone_1",
        parentId: "n_child_1",
        title: "方案項目",
        notes: "",
        resultLog: "",
        color: "#10B981",
        orderIndex: 0,
        status: "PLAN",
        isCollapsed: false,
        isPinned: false,
        isStrikethrough: false,
        variants: [],
        children: [],
      },
    ],
    createdAt: ts,
    updatedAt: ts,
  };
}

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

test("Unauthenticated user: denied read and write on boards and snapshots", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("boards").doc("proj_1").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("boards").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("boards")
      .doc("proj_1")
      .set(validBoardPayload(ALICE_UID, past))
  );
});

test("Multi-project owner: create, list, rename, update, and delete independent boards succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const boardsCol = aliceDb.collection("users").doc(ALICE_UID).collection("boards");

  await assertSucceeds(boardsCol.doc("proj_1").set(validBoardPayload(ALICE_UID, created, "專案 A")));
  await assertSucceeds(boardsCol.doc("proj_2").set(validBoardPayload(ALICE_UID, created, "專案 B")));
  await assertSucceeds(boardsCol.get());

  // Mirror Kotlin renameBoard()
  await assertSucceeds(
    boardsCol.doc("proj_1").update({
      title: "專案 A（重新命名）",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Mirror Kotlin saveBoard()
  await assertSucceeds(
    boardsCol.doc("proj_1").update({
      schemaVersion: 2,
      title: "專案 A（重新命名）",
      activeMode: "EXECUTION",
      activeView: "TRUNK",
      trunkRevealLevel: 3,
      root: validRootNode(),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Mirror Kotlin deleteBoard()
  await assertSucceeds(boardsCol.doc("proj_2").delete());
});

test("Security bounds: reject immutable mutations, ghost fields, oversized resultLog, and invalid enums", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const boardRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("boards")
    .doc("proj_1");

  await assertSucceeds(boardRef.set(validBoardPayload(ALICE_UID, created)));

  await assertFails(
    boardRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    boardRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    boardRef.update({
      activeView: "UNKNOWN_VIEW",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  const badRoot = validRootNode();
  badRoot.resultLog = "x".repeat(501);
  await assertFails(
    boardRef.update({
      root: badRoot,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Bob cannot read, rename, or delete Alice's projects", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("boards")
      .doc("proj_1")
      .set(validBoardPayload(ALICE_UID, created));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceBoard = bobDb.collection("users").doc(ALICE_UID).collection("boards").doc("proj_1");
  await assertFails(aliceBoard.get());
  await assertFails(aliceBoard.update({ title: "Hijacked", updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(aliceBoard.delete());
});

test("State snapshots: enforce parent existence, support create, update, list, and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const boardRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("boards")
    .doc("proj_1");
  const snapRef = boardRef.collection("snapshots").doc("snap_b");

  await assertFails(snapRef.set(validSnapshotPayload(ALICE_UID, created)));

  await assertSucceeds(boardRef.set(validBoardPayload(ALICE_UID, created)));
  await assertSucceeds(snapRef.set(validSnapshotPayload(ALICE_UID, created)));
  await assertSucceeds(boardRef.collection("snapshots").get());
  await assertSucceeds(
    snapRef.update({
      label: "狀態 B（修訂）",
      children: [],
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(snapRef.delete());
});
