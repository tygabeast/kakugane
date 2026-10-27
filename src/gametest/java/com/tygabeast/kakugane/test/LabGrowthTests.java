package com.tygabeast.kakugane.test;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.world.LabTracker;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Lab growth while nobody is there (DISCOVERY / ALWAYS modes). These skip time by handing the lab clock a later
 * game time, which is exactly what happens when a player comes back after days away.
 */
@GameTestHolder(Kakugane.MODID)
@PrefixGameTestTemplate(false)
public class LabGrowthTests {
    private static final long DAY = 24000L;

    private static LabTracker.Lab trackedLab(long start) {
        LabTracker.Lab lab = new LabTracker.Lab();
        lab.tracked = true;
        lab.growthStart = start;
        return lab;
    }

    @GameTest(template = "empty")
    public static void labCatchesUpAfterDaysAway(GameTestHelper h) {
        LabTracker.Lab lab = trackedLab(0);
        for (long v = 1; v <= 3; v++) lab.destroyed.add(v); // three broken vats
        boolean grew = lab.catchUp(7 * DAY, 3, false);
        h.assertTrue(grew, "a week away counts");
        h.assertTrue(lab.days == 7, "7 days (" + lab.days + ")");
        h.assertTrue(lab.stage == 2, "Spreading after 7 days at 3 per stage (" + lab.stage + ")");
        h.assertTrue(lab.regrowOwed == 3, "owes all three broken vats (" + lab.regrowOwed + ")");
        h.assertTrue(lab.restockOwed == 7, "owes guards up to the Spreading count of 7 (" + lab.restockOwed + ")");
        h.assertTrue(lab.grewUnseen, "remembers it grew unseen, for the 'while you were away' message");
        // Calling again at the same time changes nothing.
        h.assertFalse(lab.catchUp(7 * DAY, 3, false), "no double counting");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void labStopsAtOverrun(GameTestHelper h) {
        LabTracker.Lab lab = trackedLab(0);
        lab.catchUp(500 * DAY, 3, false);
        h.assertTrue(lab.stage == 3, "never past Overrun (" + lab.stage + ")");
        h.assertTrue(lab.days == 500, "a long absence is skipped in one go (" + lab.days + ")");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void deadLeaderFreezesGrowth(GameTestHelper h) {
        LabTracker.Lab lab = trackedLab(0);
        lab.leaderDefeated = true;
        h.assertFalse(lab.catchUp(30 * DAY, 3, false), "no growth once the leader is dead");
        h.assertTrue(lab.stage == 0 && lab.days == 0, "stays where it was");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void untrackedLabDoesNotGrow(GameTestHelper h) {
        LabTracker.Lab lab = new LabTracker.Lab(); // not discovered yet
        h.assertFalse(lab.catchUp(30 * DAY, 3, false), "an undiscovered lab waits");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void alwaysModeWaitsAStageFirst(GameTestHelper h) {
        LabTracker t = new LabTracker();
        LabTracker.Lab lab = new LabTracker.Lab();
        t.track(lab, 1000, 3); // ALWAYS: one extra stage's wait
        h.assertFalse(lab.catchUp(1000 + 2 * DAY, 3, false), "nothing in the grace period");
        lab.catchUp(1000 + 6 * DAY, 3, false);
        h.assertTrue(lab.stage == 1, "first stage after grace plus one stage (" + lab.stage + ")");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void trackingKeepsEarnedDays(GameTestHelper h) {
        LabTracker t = new LabTracker();
        LabTracker.Lab lab = new LabTracker.Lab();
        lab.days = 4; // earned under presence mode
        lab.stage = 1;
        t.track(lab, 100 * DAY, 0);
        h.assertFalse(lab.catchUp(100 * DAY, 3, false), "switching modes doesn't add days");
        lab.catchUp(102 * DAY, 3, false);
        h.assertTrue(lab.days == 6 && lab.stage == 2, "carries on from day 4 (" + lab.days + ", stage " + lab.stage + ")");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void longAbsenceWithSlowStages(GameTestHelper h) {
        LabTracker.Lab lab = trackedLab(0);
        lab.catchUp(500 * DAY, 20, false); // 20 days per stage: the 30-step loop alone would stop at stage 1
        h.assertTrue(lab.stage == 3, "the skipped days still count toward the stage (" + lab.stage + ")");
        h.succeed();
    }
}
