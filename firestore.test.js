const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_aura_123";
const BOB_UID = "bob_aura_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function pastDate(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

// 1. Unauthenticated denial
test("Unauthenticated user cannot read or write profiles, tasks, or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = pastDate();

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("messages").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set({
      userId: ALICE_UID,
      title: "Rogue task",
      prompt: "Do something",
      category: "GENERAL",
      priority: "NORMAL",
      status: "QUEUED",
      scheduleMode: "ONCE",
      assignedModels: ["Gemini 3.8 Flash"],
      executionSteps: [],
      progressPercent: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
});

// 2. UserProfile create, action-based partial update, shadow field & cross-user rejection
test("UserProfile: owner create/update succeeds; ghost fields & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      preferredModelId: "auto-swarm",
      swarmEnabled: true,
      maxPowerEnabled: true,
      autoSearchEnabled: true,
      totalSwarmRuns: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Action 1: Update preferences
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredModelId: "nvidia/llama-3.1-nemotron-ultra-253b-v1",
      maxPowerEnabled: true,
      updatedAt: pastDate(1000),
    })
  );

  // Action 2: Increment swarm telemetry
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalSwarmRuns: 1,
      updatedAt: pastDate(500),
    })
  );

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(200),
    })
  );

  // Immutable createdAt mutation must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Cross-user read & write must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(100),
    })
  );
});

// 3. AssistantTask lifecycle, terminal state locking, future timestamp rejection, & list query
test("AssistantTask: Kotlin create, progress update, completion lock, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  // Create valid QUEUED task (no `id` field, optional resultText omitted)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Research latest AI papers",
      prompt: "Summarize top 3 LLM orchestration breakthroughs today",
      category: "RESEARCH",
      priority: "HIGH",
      status: "QUEUED",
      scheduleMode: "CONTINUOUS_24H",
      assignedModels: ["Nemotron Ultra 253B", "DeepSeek R1", "Gemini 3.8 Flash"],
      executionSteps: ["Queued for 24h swarm"],
      progressPercent: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject extra @DocumentId `id` field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_bad_id").set({
      id: "task_bad_id",
      userId: ALICE_UID,
      title: "Invalid",
      prompt: "Test",
      category: "GENERAL",
      priority: "NORMAL",
      status: "QUEUED",
      scheduleMode: "ONCE",
      assignedModels: [],
      executionSteps: [],
      progressPercent: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_future").set({
      userId: ALICE_UID,
      title: "Future",
      prompt: "Test",
      category: "GENERAL",
      priority: "NORMAL",
      status: "QUEUED",
      scheduleMode: "ONCE",
      assignedModels: [],
      executionSteps: [],
      progressPercent: 0,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Update progress to RUNNING
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      status: "RUNNING",
      progressPercent: 45,
      executionSteps: ["Planner routed task", "Web search active"],
      updatedAt: pastDate(2000),
    })
  );

  // Complete task with resultText
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      status: "COMPLETED",
      progressPercent: 100,
      resultText: "Synthesized executive report completed by 3 models.",
      executionSteps: ["Planner routed task", "Web search active", "Final synthesis verified"],
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state lock: COMPLETED task cannot be mutated further
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      status: "RUNNING",
      progressPercent: 10,
      updatedAt: pastDate(500),
    })
  );

  // Owner list query succeeds; cross-user list & delete fail
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").orderBy("createdAt", "desc").get()
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").delete());

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").delete()
  );
});

// 4. ChatMessage append-only creation, update rejection, list & cross-user isolation
test("ChatMessage: owner create/list/delete succeed; updates & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(3000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "ASSISTANT",
      content: "Here is the multi-model synthesis with live Google Search results.",
      modelUsed: "Aura Max Swarm (3 Models)",
      swarmModels: ["DeepSeek R1", "Llama 3.3 70B", "Gemini 3.8 Flash"],
      searchSources: ["https://build.nvidia.com", "https://ai.google.dev"],
      usedWebSearch: true,
      hasScreenContext: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Messages are immutable (append-only)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").update({
      content: "Tampered",
      updatedAt: pastDate(500),
    })
  );

  // Owner list succeeds; Bob cannot read Alice's chat
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").orderBy("createdAt", "asc").get()
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("messages").get());

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").delete()
  );
});
