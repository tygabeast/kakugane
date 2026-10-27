package com.tygabeast.kakugane.test;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.core.ActiveForms;
import com.tygabeast.kakugane.core.Affinity;
import com.tygabeast.kakugane.core.HeartData;
import com.tygabeast.kakugane.core.Hearts;
import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.Milestones;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.data.Trait;
import com.tygabeast.kakugane.entity.Hound;
import com.tygabeast.kakugane.item.KakuganeItem;
import com.tygabeast.kakugane.item.FormArmorItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Server-side checks of the core rules. Run with: gradle runGameTestServer */
@GameTestHolder(Kakugane.MODID)
@PrefixGameTestTemplate(false)
public class KakuganeGameTests {

    static ItemStack formed(Archetype form, int serial) {
        ItemStack s = new ItemStack(ModRegistry.KAKUGANE.get());
        s.set(ModRegistry.KAKUGANE_DATA.get(), KakuganeData.EMPTY.withSerial(serial).withForm(form, Trait.pick(serial, form.worn()), ""));
        return s;
    }

    private static KakuganeData data(ItemStack s) {
        return KakuganeItem.data(s);
    }

    /**
     * Like GameTestHelper.makeMockServerPlayerInLevel, but the fake connection accepts modded packets (Curios syncs
     * its slots to every player that joins, and the vanilla mock connection refuses anything non-vanilla).
     * Like the vanilla mock player, it counts as creative.
     */
    static ServerPlayer mockPlayer(GameTestHelper h) {
        return mockPlayer(h, true);
    }

    static ServerPlayer mockPlayer(GameTestHelper h, boolean creative) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() { return false; }

            @Override
            public boolean isCreative() { return creative; }
        };
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        return p;
    }

    @GameTest(template = "empty")
    public static void imprintNeedsCombatThenActivates(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.KAKUGANE.get()));
        ActiveForms.activateStack(p, p.getMainHandItem());
        KakuganeData d = data(p.getMainHandItem());
        h.assertTrue(d.serial() > 0, "serial assigned");
        h.assertTrue(d.isOwnedBy(p.getUUID()), "bonded on first activation");
        h.assertFalse(d.formed() || d.active(), "stays unformed without combat");

        Affinity.record(p, Archetype.BOW, 50);
        ActiveForms.activateStack(p, p.getMainHandItem());
        d = data(p.getMainHandItem());
        h.assertTrue(d.form().orElse(null) == Archetype.BOW && d.active(), "imprints to bow and activates");
        h.assertTrue(d.traitValue().map(t -> t.fits(false)).orElse(false), "held trait");
        h.assertTrue(KakuganeAccess.state(p).activeForm == Archetype.BOW, "state cached");

        ActiveForms.toggle(p);
        h.assertFalse(data(p.getMainHandItem()).active(), "toggle reverts");
        h.assertTrue(p.getMainHandItem().get(DataComponents.ATTRIBUTE_MODIFIERS) == null
                || p.getMainHandItem().get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().isEmpty(), "weapon stats removed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silverSkinStoresAndReturnsArmor(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        p.setItemInHand(InteractionHand.MAIN_HAND, formed(Archetype.SILVER_SKIN, 901));
        h.assertTrue(ActiveForms.activateStack(p, p.getMainHandItem()), "activates");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof FormArmorItem, "suit chest on");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof FormArmorItem, "suit boots on");
        ItemContainerContents stored = p.getMainHandItem().get(ModRegistry.STORED_ARMOR.get());
        h.assertTrue(stored != null, "old armor stored");
        NonNullList<ItemStack> list = NonNullList.withSize(4, ItemStack.EMPTY);
        stored.copyInto(list);
        h.assertTrue(list.get(1).is(Items.IRON_CHESTPLATE), "iron chestplate kept in slot order");

        p.getInventory().tick(); // pieces in their slots must survive a tick
        h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof FormArmorItem, "pieces survive while active");

        ActiveForms.deactivate(p);
        h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "chestplate returned");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "suit removed");
        h.assertTrue(p.getMainHandItem().get(ModRegistry.STORED_ARMOR.get()) == null, "storage cleared");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heldFormStaysActiveAndEmbryosCanBeCrushed(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, formed(Archetype.LANCE, 902));
        p.getInventory().setItem(1, new ItemStack(ModRegistry.EMBRYO.get(), 3));
        ActiveForms.activateStack(p, p.getInventory().getItem(0));
        p.getInventory().selected = 1;
        p.getInventory().tick();
        h.assertTrue(data(p.getInventory().getItem(0)).active(), "stays active when not held");
        h.assertTrue(KakuganeAccess.state(p).hasActive(), "cache kept");
        ModRegistry.EMBRYO.get().use(p.level(), p, InteractionHand.MAIN_HAND);
        // (The mock player is in creative, where the embryo isn't used up; the deed is what matters.)
        h.assertTrue(data(p.getInventory().getItem(0)).deeds().embryos() == 1, "embryo crushed with the lance in another slot");
        // Move the active plate to the main inventory: still active.
        p.getInventory().setItem(20, p.getInventory().getItem(0));
        p.getInventory().setItem(0, ItemStack.EMPTY);
        ActiveForms.validate(p, KakuganeAccess.state(p));
        p.getInventory().tick();
        h.assertTrue(data(p.getInventory().getItem(20)).active() && KakuganeAccess.state(p).hasActive(), "survives a move");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void missingFormRevertsAfterGrace(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h); // the mock player counts as creative: 60 s grace
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        p.setItemInHand(InteractionHand.MAIN_HAND, formed(Archetype.SILVER_SKIN, 911));
        ActiveForms.activateStack(p, p.getMainHandItem());
        ItemStack plate = p.getMainHandItem().copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // e.g. lifted in the creative inventory
        PlayerState st = KakuganeAccess.state(p);
        for (int i = 0; i < 59; i++) ActiveForms.validate(p, st);
        h.assertTrue(st.hasActive() && p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof FormArmorItem, "grace period");
        p.setItemInHand(InteractionHand.MAIN_HAND, plate); // put back down
        ActiveForms.validate(p, st);
        h.assertTrue(st.hasActive() && st.missingChecks == 0, "found again, counter reset");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (int i = 0; i < 60; i++) ActiveForms.validate(p, st);
        h.assertFalse(st.hasActive(), "reverted after the grace period");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "suit removed");
        // The plate turning up again returns the armor stored inside it.
        p.setItemInHand(InteractionHand.MAIN_HAND, plate);
        p.getInventory().tick();
        h.assertFalse(data(p.getMainHandItem()).active(), "plate reverted");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "armor returned");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyOneActive(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        p.getInventory().setItem(0, formed(Archetype.LANCE, 903));
        p.getInventory().setItem(1, formed(Archetype.BREAKER, 904));
        p.getInventory().selected = 0;
        ActiveForms.activateStack(p, p.getInventory().getItem(0));
        p.getInventory().selected = 1;
        ActiveForms.activateStack(p, p.getInventory().getItem(1));
        h.assertFalse(data(p.getInventory().getItem(0)).active(), "first reverted");
        h.assertTrue(data(p.getInventory().getItem(1)).active(), "second active");
        h.assertTrue(KakuganeAccess.state(p).activeSerial == 904, "cache points at second");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void houndResistsMundaneDamage(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        Hound hound = h.spawn(ModRegistry.HOUND.get(), new BlockPos(1, 1, 1));
        float start = hound.getHealth();
        hound.hurt(p.damageSources().playerAttack(p), 10f);
        float mundane = start - hound.getHealth();
        hound.setHealth(start);
        hound.invulnerableTime = 0;

        p.setItemInHand(InteractionHand.MAIN_HAND, formed(Archetype.LANCE, 905));
        ActiveForms.activateStack(p, p.getMainHandItem());
        hound.hurt(p.damageSources().playerAttack(p), 10f);
        float alchemical = start - hound.getHealth();
        h.assertTrue(mundane < 3.0f, "mundane hit reduced (took " + mundane + ")");
        h.assertTrue(alchemical > 9.0f, "Kakugane hit lands fully (took " + alchemical + ")");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heartSavesOnce(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        p.getInventory().setItem(5, new ItemStack(ModRegistry.KAKUGANE.get()));
        p.die(p.damageSources().generic());
        HeartData heart = KakuganeAccess.heart(p);
        h.assertTrue(heart.has() && heart.used, "heart taken");
        h.assertTrue(p.getInventory().getItem(5).isEmpty(), "item consumed");
        h.assertTrue(p.getHealth() > 0, "player alive");
        h.assertTrue(heart.data().isOwnedBy(p.getUUID()) && heart.data().heart(), "heart bonded");
        p.getInventory().setItem(5, new ItemStack(ModRegistry.KAKUGANE.get()));
        h.assertFalse(Hearts.trySave(p), "only once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heartHeldFormProjection(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        HeartData heart = KakuganeAccess.heart(p);
        ItemStack s = formed(Archetype.LANCE, 906);
        s.set(ModRegistry.KAKUGANE_DATA.get(), data(s).bondTo(p.getUUID(), "test").withHeart(true));
        heart.stack = s;
        heart.used = true;
        p.getInventory().selected = 0;
        ActiveForms.toggle(p);
        h.assertTrue(data(p.getMainHandItem()).heart() && data(p.getMainHandItem()).active(), "projection in hand");
        p.getInventory().selected = 2;
        p.getInventory().tick();
        h.assertTrue(data(p.getInventory().getItem(0)).heart() && KakuganeAccess.state(p).hasActive(), "projection stays when switched away");
        ActiveForms.toggle(p);
        h.assertTrue(p.getInventory().getItem(0).isEmpty(), "projection vanishes on revert");
        h.assertFalse(KakuganeAccess.state(p).hasActive(), "heart form reverted");
        h.assertTrue(heart.has() && !heart.data().active(), "real heart kept, inactive");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void milestoneDeepen(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        ItemStack s = formed(Archetype.BOW, 907);
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        ActiveForms.activateStack(p, p.getMainHandItem());
        Milestones.credit(p, KakuganeData.Deeds::withHuman);
        h.assertTrue(data(p.getMainHandItem()).pending(), "milestone 1 pending");
        h.assertTrue(Milestones.choose(p, true) == 1, "deepen accepted");
        KakuganeData d = data(p.getMainHandItem());
        h.assertTrue(d.tier() == 1 && d.milestones() == 1 && !d.pending(), "tier 1");
        h.assertTrue(d.active() && KakuganeAccess.state(p).activeTier == 1, "re-activated with new tier");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void newWielderResetsTierKeepsForm(GameTestHelper h) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        KakuganeData d = KakuganeData.EMPTY.withSerial(5).bondTo(a, "Alpha")
                .withForm(Archetype.LANCE, Trait.SWIFT, "Alpha").withTier(2);
        KakuganeData same = d.unbond().bondTo(a, "Alpha");
        h.assertTrue(same.tier() == 2, "same wielder keeps tier");
        KakuganeData other = d.unbond().bondTo(b, "Beta");
        h.assertTrue(other.tier() == 0 && other.form().orElse(null) == Archetype.LANCE, "new wielder: tier 0, form kept");
        h.assertTrue(other.lineage().equals(java.util.List.of("Alpha", "Beta")) && other.shaper().equals("Alpha"), "lineage and shaper");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void embryosCanBeFirstMilestoneAndGivenTierSurvivesFirstBond(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        ItemStack s = formed(Archetype.BREAKER, 909);
        s.set(ModRegistry.KAKUGANE_DATA.get(), data(s).withTier(2));
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        ActiveForms.activateStack(p, p.getMainHandItem());
        h.assertTrue(data(p.getMainHandItem()).tier() == 2, "first bond keeps a given tier");
        for (int i = 0; i < 10; i++) Milestones.credit(p, KakuganeData.Deeds::addEmbryo);
        h.assertTrue(data(p.getMainHandItem()).pending(), "embryos alone reach a milestone");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silverSkinPiecesAreCursed(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, formed(Archetype.SILVER_SKIN, 910));
        ActiveForms.activateStack(p, p.getMainHandItem());
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        h.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.has(chest,
                net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE), "binding");
        // Re-activating while pieces are on must not store the old pieces as "armor".
        ActiveForms.deactivate(p);
        ActiveForms.activateStack(p, p.getMainHandItem());
        NonNullList<ItemStack> list = NonNullList.withSize(4, ItemStack.EMPTY);
        p.getMainHandItem().get(ModRegistry.STORED_ARMOR.get()).copyInto(list);
        h.assertTrue(list.stream().allMatch(ItemStack::isEmpty), "nothing stored");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bondedRefusesOthers(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        ItemStack s = formed(Archetype.LANCE, 908);
        s.set(ModRegistry.KAKUGANE_DATA.get(), data(s).bondTo(UUID.randomUUID(), "Someone"));
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        h.assertFalse(ActiveForms.activateStack(p, p.getMainHandItem()), "refused");
        PlayerState st = KakuganeAccess.state(p);
        h.assertFalse(st.hasActive(), "nothing active");
        h.succeed();
    }

    // ---- wave 2: labs and new Homunculi -------------------------------------------------------------

    @GameTest(template = "empty")
    public static void ramPlatingDeflectsFrontHits(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        com.tygabeast.kakugane.entity.Ram ram = h.spawn(ModRegistry.RAM.get(), new BlockPos(1, 1, 1));
        ram.setNoAi(true);
        ram.setYRot(0f);
        ram.yBodyRot = 0f; // facing +z (south)
        float start = ram.getHealth();
        p.moveTo(ram.getX(), ram.getY(), ram.getZ() + 2, 180f, 0f); // in front
        ram.hurt(p.damageSources().playerAttack(p), 8f);
        float front = start - ram.getHealth();
        ram.setHealth(start);
        ram.invulnerableTime = 0;
        p.moveTo(ram.getX(), ram.getY(), ram.getZ() - 2, 0f, 0f); // behind
        ram.hurt(p.damageSources().playerAttack(p), 8f);
        float back = start - ram.getHealth();
        h.assertTrue(front < back * 0.6f, "front hit deflected (front " + front + ", back " + back + ")");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void leaderTransformsRegeneratesAndSummons(GameTestHelper h) {
        ServerPlayer p = mockPlayer(h);
        com.tygabeast.kakugane.entity.LabLeader leader = h.spawn(ModRegistry.LAB_LEADER.get(), new BlockPos(1, 1, 1));
        leader.setMotif(com.tygabeast.kakugane.entity.LabLeader.Motif.RAM);
        h.assertFalse(leader.transformed(), "starts in human form");
        float max = leader.getMaxHealth();
        leader.hurt(p.damageSources().playerAttack(p), 4f); // mundane: reduced, and it should heal back
        h.assertTrue(leader.transformed(), "transforms when attacked");
        float afterMundane = leader.getHealth();
        h.runAfterDelay(40, () -> {
            h.assertTrue(leader.getHealth() > afterMundane, "regenerates after mundane damage");
            // Kakugane damage stops the regeneration.
            p.setItemInHand(InteractionHand.MAIN_HAND, formed(Archetype.LANCE, 912));
            ActiveForms.activateStack(p, p.getMainHandItem());
            leader.invulnerableTime = 0;
            // A Ram-motif leader shrugs off frontal hits, so turn its back to the player for this one.
            float toPlayer = (float) Math.toDegrees(Math.atan2(p.getZ() - leader.getZ(), p.getX() - leader.getX())) - 90f;
            leader.yBodyRot = leader.yBodyRotO = toPlayer + 180f;
            leader.hurt(p.damageSources().playerAttack(p), max * 0.6f);
            float afterAlchemical = leader.getHealth();
            h.assertTrue(afterAlchemical <= max * 0.5f, "took the big hit (" + afterAlchemical + ")");
            int rams = h.getLevel().getEntitiesOfClass(com.tygabeast.kakugane.entity.Ram.class, leader.getBoundingBox().inflate(6)).size();
            h.assertTrue(rams == 2, "summoned two Rams at half health (" + rams + ")");
            h.runAfterDelay(40, () -> {
                h.assertTrue(leader.getHealth() <= afterAlchemical + 0.01f, "no regeneration right after Kakugane damage");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void leaderStageScalesHealth(GameTestHelper h) {
        com.tygabeast.kakugane.entity.LabLeader leader = h.spawn(ModRegistry.LAB_LEADER.get(), new BlockPos(1, 1, 1));
        float base = leader.getMaxHealth();
        leader.setStage(2);
        h.assertTrue(Math.abs(leader.getMaxHealth() - base * 1.5f) < 0.01f, "stage 2 = +50% health");
        h.assertTrue(Math.abs(leader.getHealth() - leader.getMaxHealth()) < 0.01f, "health ratio kept");
        leader.setStage(0);
        h.assertTrue(Math.abs(leader.getMaxHealth() - base) < 0.01f, "back to base");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void websPassThroughHomunculi(GameTestHelper h) {
        com.tygabeast.kakugane.entity.Crawler crawler = h.spawn(ModRegistry.CRAWLER.get(), new BlockPos(1, 1, 0));
        crawler.setNoAi(true);
        Hound hound = h.spawn(ModRegistry.HOUND.get(), new BlockPos(1, 1, 3));
        hound.setNoAi(true);
        float start = hound.getHealth();
        // A web fired from the Crawler straight through the Hound.
        var web = new com.tygabeast.kakugane.entity.WebShot(h.getLevel(), crawler, 3f);
        var at = hound.position();
        web.moveTo(at.x, at.y + 0.5, at.z - 1.5, 0f, 0f);
        web.shoot(0, 0, 1, 1.0f, 0f);
        h.getLevel().addFreshEntity(web);
        h.runAfterDelay(20, () -> {
            h.assertTrue(hound.getHealth() >= start, "Hound untouched by a Crawler's web");
            h.assertFalse(hound.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "and not slowed");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void vatsResistPistons(GameTestHelper h) {
        var state = ModRegistry.EMBRYO_VAT.get().defaultBlockState();
        h.assertTrue(state.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK, "vats can't be pushed");
        h.succeed();
    }
}
