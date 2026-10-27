package com.tygabeast.kakugane.test;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.ability.AliceWings;
import com.tygabeast.kakugane.ability.Gauntlets;
import com.tygabeast.kakugane.ability.Mantle;
import com.tygabeast.kakugane.core.ActiveForms;
import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.item.FormArmorItem;
import com.tygabeast.kakugane.item.FormCurioItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

/** Server-side checks of the worn forms added in 0.3.0. Run with: gradle runGameTestServer */
@GameTestHolder(Kakugane.MODID)
@PrefixGameTestTemplate(false)
public class WornFormTests {

    private static ItemStack curio(ServerPlayer p, String slot) {
        return CuriosApi.getCuriosInventory(p).flatMap(inv -> inv.getStacksHandler(slot))
                .map(h -> h.getStacks().getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }

    private static ServerPlayer wearing(GameTestHelper h, Archetype form, int serial, boolean creative) {
        ServerPlayer p = KakuganeGameTests.mockPlayer(h, creative);
        p.setItemInHand(InteractionHand.MAIN_HAND, KakuganeGameTests.formed(form, serial));
        h.assertTrue(ActiveForms.activateStack(p, p.getMainHandItem()), form.id() + " activates");
        // Fists free for the tests that punch: move the Kakugane off the hand (it stays active in the inventory).
        ItemStack k = p.getMainHandItem();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.getInventory().setItem(8, k);
        return p;
    }

    @GameTest(template = "empty")
    public static void curioFormsGoOnAndComeOff(GameTestHelper h) {
        Object[][] forms = {
                {Archetype.AERIAL_OPERATOR, "head"}, {Archetype.ALICE, "back"}, {Archetype.GAUNTLETS, "hands"},
                {Archetype.MANTLE, "back"}, {Archetype.BLADE_BELT, "belt"}};
        int serial = 940;
        for (Object[] f : forms) {
            Archetype form = (Archetype) f[0];
            String slot = (String) f[1];
            ServerPlayer p = wearing(h, form, serial++, true);
            ItemStack piece = curio(p, slot);
            h.assertTrue(piece.getItem() instanceof FormCurioItem c && c.form() == form, form.id() + " piece in the " + slot + " slot");
            ActiveForms.deactivate(p);
            h.assertTrue(curio(p, slot).isEmpty(), form.id() + " piece removed on revert");
        }
        h.succeed();
    }

    /** The pieces must survive Curios ticking them (it calls inventoryTick with slot -1) for more than a moment. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void curioPiecesStayOn(GameTestHelper h) {
        Object[][] forms = {
                {Archetype.AERIAL_OPERATOR, "head"}, {Archetype.ALICE, "back"}, {Archetype.GAUNTLETS, "hands"},
                {Archetype.MANTLE, "back"}, {Archetype.BLADE_BELT, "belt"}};
        int serial = 970;
        java.util.List<Runnable> checks = new java.util.ArrayList<>();
        for (Object[] f : forms) {
            Archetype form = (Archetype) f[0];
            String slot = (String) f[1];
            ServerPlayer p = wearing(h, form, serial++, false);
            checks.add(() -> h.assertTrue(curio(p, slot).getItem() instanceof FormCurioItem c && c.form() == form,
                    form.id() + " piece still in the " + slot + " slot after 20 ticks"));
        }
        h.runAfterDelay(20, () -> {
            checks.forEach(Runnable::run);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void greavesStoreAndReturnLeggings(GameTestHelper h) {
        ServerPlayer p = KakuganeGameTests.mockPlayer(h);
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        p.setItemInHand(InteractionHand.MAIN_HAND, KakuganeGameTests.formed(Archetype.GREAVES, 950));
        h.assertTrue(ActiveForms.activateStack(p, p.getMainHandItem()), "activates");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof FormArmorItem f && f.form() == Archetype.GREAVES, "greaves on");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "other armor untouched");
        ActiveForms.deactivate(p);
        h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS), "leggings returned");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aerialOperatorFiltersPoison(GameTestHelper h) {
        ServerPlayer p = wearing(h, Archetype.AERIAL_OPERATOR, 951, true);
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        h.assertFalse(p.hasEffect(MobEffects.POISON), "poison doesn't take hold");
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
        h.assertTrue(p.hasEffect(MobEffects.WEAKNESS), "other effects still do");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gauntletsChainBuildsToAFinisher(GameTestHelper h) {
        ServerPlayer p = wearing(h, Archetype.GAUNTLETS, 952, true);
        PlayerState st = KakuganeAccess.state(p);
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        long t = 1000;
        float first = Gauntlets.onMelee(p, st, z, 1f, t);
        h.assertTrue(Math.abs(first - 4f) < 0.01f, "bare fist +3 at tier I (" + first + ")");
        float last = first;
        for (int i = 1; i < 4; i++) {
            st.lastMeleeTick = t;
            t += 10;
            float dmg = Gauntlets.onMelee(p, st, z, 1f, t);
            h.assertTrue(dmg > last, "each chained hit harder (" + dmg + " after " + last + ")");
            last = dmg;
        }
        st.lastMeleeTick = t;
        t += 10;
        float finisher = Gauntlets.onMelee(p, st, z, 1f, t);
        h.assertTrue(finisher >= 6f && st.combo == 0, "full chain ends in a finisher (" + finisher + ")");
        st.lastMeleeTick = t;
        float slow = Gauntlets.onMelee(p, st, z, 1f, t + 100);
        h.assertTrue(st.combo == 1 && Math.abs(slow - 4f) < 0.01f, "a long pause starts a new chain");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bladeBeltFollowsUpHits(GameTestHelper h) {
        ServerPlayer p = wearing(h, Archetype.BLADE_BELT, 953, true);
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        z.setNoAi(true);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        float start = z.getHealth();
        z.hurt(p.damageSources().playerAttack(p), 4f);
        float lost = start - z.getHealth();
        h.assertTrue(lost >= 5.9f, "the hit plus an arm strike (" + lost + ")");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aliceReserveDrainsAndRunsDry(GameTestHelper h) {
        ServerPlayer p = wearing(h, Archetype.ALICE, 954, false);
        PlayerState st = KakuganeAccess.state(p);
        p.setOnGround(true);
        AliceWings.tick(p, st, 0);
        h.assertTrue(p.getAbilities().mayfly, "wings grant flight");
        float full = st.wingReserve;
        p.setOnGround(false);
        p.getAbilities().flying = true;
        for (int i = 0; i < 20; i++) {
            p.setPos(p.getX(), p.getY() + 0.2, p.getZ()); // climbing
            AliceWings.tick(p, st, i);
        }
        h.assertTrue(st.wingReserve < full - 50, "climbing drains the reserve (" + st.wingReserve + " of " + full + ")");
        AliceWings.onHurt(p, st);
        st.wingReserve = 1;
        p.setPos(p.getX(), p.getY() + 0.2, p.getZ());
        AliceWings.tick(p, st, 30);
        h.assertTrue(st.wingsDry && !p.getAbilities().mayfly && !p.getAbilities().flying, "running dry ends flight");
        ActiveForms.deactivate(p);
        h.assertFalse(p.getAbilities().mayfly, "no flight left after the wings are gone");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mantleFadesWhenStillAndStrikesHarder(GameTestHelper h) {
        ServerPlayer p = wearing(h, Archetype.MANTLE, 955, true);
        PlayerState st = KakuganeAccess.state(p);
        for (int i = 0; i < 12; i++) Mantle.tick(p, st, i * 4L);
        h.assertTrue(st.stealthed && p.hasEffect(MobEffects.INVISIBILITY), "fades after standing still");
        float dmg = Mantle.onAttack(p, st, 4f, 100);
        h.assertTrue(Math.abs(dmg - 6f) < 0.01f && !st.stealthed && !p.hasEffect(MobEffects.INVISIBILITY), "stealth hit +50%, then revealed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wornFormSignalsAreRecorded(GameTestHelper h) {
        ServerPlayer p = KakuganeGameTests.mockPlayer(h);
        var aff = p.getData(ModRegistry.AFFINITY.get());
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        z.setNoAi(true);
        z.hurt(p.damageSources().playerAttack(p), 1f); // a bare fist on a zombie that isn't after you
        h.assertTrue(aff.score("gauntlets") > 0, "fist hit counts for the Gauntlets");
        h.assertTrue(aff.score("mantle") > 0, "hitting something unaware counts for the Mantle");
        // Kill one of four zombies: surrounded.
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 2.5));
        p.moveTo(at.x, at.y, at.z);
        for (int i = 0; i < 3; i++) h.spawn(EntityType.ZOMBIE, new BlockPos(3 + i, 1, 3)).setNoAi(true);
        Zombie victim = h.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
        victim.setNoAi(true);
        victim.hurt(p.damageSources().playerAttack(p), 1000f);
        h.assertTrue(aff.score("blade_belt") >= 1.0, "a kill while surrounded counts for the Blade Belt");
        h.succeed();
    }
    // ---- review fixes ------------------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void droppingActiveWingsTakesFlightAway(GameTestHelper h) {
        ServerPlayer p = wearing(h, Archetype.ALICE, 980, false);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // the test server's default mode may allow flight
        h.assertFalse(p.getAbilities().mayfly, "no flight before the wings act");
        // Mock players aren't ticked by a network connection, so run the wings' tick by hand.
        var st = com.tygabeast.kakugane.core.KakuganeAccess.state(p);
        com.tygabeast.kakugane.ability.WornAbilities.tick(p, st, com.tygabeast.kakugane.core.Affinity.now(p));
        h.runAfterDelay(1, () -> {
            h.assertTrue(p.getAbilities().mayfly, "the wings grant flight");
            ActiveForms.deactivateStack(p, p.getInventory().getItem(8)); // what throwing the plate does
            h.assertFalse(p.getAbilities().mayfly, "flight goes with the wings");
            h.assertFalse(com.tygabeast.kakugane.core.KakuganeAccess.state(p).hasActive(), "nothing active");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void wingReserveSurvivesReactivation(GameTestHelper h) {
        ServerPlayer p = wearing(h, Archetype.ALICE, 981, false);
        h.runAfterDelay(3, () -> {
            var st = com.tygabeast.kakugane.core.KakuganeAccess.state(p);
            st.wingReserve = 1f;
            st.wingsDry = true;
            ItemStack plate = p.getInventory().getItem(8);
            ActiveForms.deactivate(p);
            h.assertTrue(ActiveForms.activateStack(p, plate), "back on");
            h.assertTrue(st.wingReserve == 1f && st.wingsDry, "switching the wings off and on doesn't refill them");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void reactivatingAStaleActivePlateKeepsStoredArmor(GameTestHelper h) {
        ServerPlayer p = KakuganeGameTests.mockPlayer(h, false);
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        p.setItemInHand(InteractionHand.MAIN_HAND, KakuganeGameTests.formed(Archetype.GREAVES, 982));
        ItemStack plate = p.getMainHandItem();
        h.assertTrue(ActiveForms.activateStack(p, plate), "activates");
        // What the 3-second check does when the plate goes missing: forget the form and strip the piece. The plate
        // itself (elsewhere at the time) is still marked active and still holds the leggings.
        com.tygabeast.kakugane.core.KakuganeAccess.state(p).clearActive();
        com.tygabeast.kakugane.core.WornForms.stripPieces(p, 982);
        h.assertTrue(ActiveForms.activateStack(p, plate), "activates again");
        ActiveForms.deactivate(p);
        int found = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(Items.DIAMOND_LEGGINGS)) found++;
        h.assertTrue(found == 1, "the diamond leggings survive, exactly once (" + found + ")");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bindingCurseBlocksGreaves(GameTestHelper h) {
        ServerPlayer p = KakuganeGameTests.mockPlayer(h, false);
        ItemStack cursed = new ItemStack(Items.IRON_LEGGINGS);
        cursed.enchant(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.BINDING_CURSE), 1);
        p.setItemSlot(EquipmentSlot.LEGS, cursed);
        p.setItemInHand(InteractionHand.MAIN_HAND, KakuganeGameTests.formed(Archetype.GREAVES, 983));
        h.assertFalse(ActiveForms.activateStack(p, p.getMainHandItem()), "can't put Greaves over cursed leggings");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS) == cursed, "the cursed leggings stay");
        h.succeed();
    }
}
