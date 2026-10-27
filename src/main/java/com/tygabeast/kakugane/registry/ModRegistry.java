package com.tygabeast.kakugane.registry;

import com.mojang.serialization.MapCodec;
import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.core.AffinityData;
import com.tygabeast.kakugane.core.HeartData;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.entity.AlchemicArrow;
import com.tygabeast.kakugane.entity.Hound;
import com.tygabeast.kakugane.entity.Crawler;
import com.tygabeast.kakugane.entity.LabLeader;
import com.tygabeast.kakugane.entity.Ram;
import com.tygabeast.kakugane.entity.WebShot;
import com.tygabeast.kakugane.block.EmbryoVatBlock;
import com.tygabeast.kakugane.world.LabPiece;
import com.tygabeast.kakugane.world.LabStructure;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import com.tygabeast.kakugane.item.EmbryoItem;
import com.tygabeast.kakugane.item.KakuganeItem;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.item.FormArmorItem;
import com.tygabeast.kakugane.item.FormCurioItem;
import com.tygabeast.kakugane.world.HomunculusSpawnModifier;
import com.mojang.serialization.Codec;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * Every object the mod adds to the game is registered here. A DeferredRegister collects entries and hands
 * them to Minecraft at the right moment during startup; the DeferredHolder fields are how the rest of
 * the code refers to them afterwards (call .get() once the game is running).
 */
public final class ModRegistry {
    // ---- Data components: extra data stored on item stacks -------------------------------------------
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Kakugane.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<KakuganeData>> KAKUGANE_DATA =
            COMPONENTS.registerComponentType("kakugane", b -> b.persistent(KakuganeData.CODEC).networkSynchronized(KakuganeData.STREAM_CODEC));

    /** Armor that a worn form displaced; given back when the form deactivates. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> STORED_ARMOR =
            COMPONENTS.registerComponentType("stored_armor", b -> b.persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** On a worn-form piece: serial of the Kakugane it belongs to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FORM_LINK =
            COMPONENTS.registerComponentType("form_link", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // ---- Armor material for Silver Skin ---------------------------------------------------------------
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Kakugane.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SILVER_SKIN_MATERIAL = ARMOR_MATERIALS.register("silver_skin",
            () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                        m.put(ArmorItem.Type.BOOTS, 3);
                        m.put(ArmorItem.Type.LEGGINGS, 6);
                        m.put(ArmorItem.Type.CHESTPLATE, 8);
                        m.put(ArmorItem.Type.HELMET, 3);
                        m.put(ArmorItem.Type.BODY, 8);
                    }),
                    0,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.EMPTY,
                    // Two texture layers: plain metal, then an accent layer tinted with the Kakugane's palette.
                    List.of(new ArmorMaterial.Layer(Kakugane.id("silver_skin")),
                            new ArmorMaterial.Layer(Kakugane.id("silver_skin"), "_accent", true)),
                    3.0f, 0.1f));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GREAVES_MATERIAL = ARMOR_MATERIALS.register("greaves",
            () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                        m.put(ArmorItem.Type.BOOTS, 0);
                        m.put(ArmorItem.Type.LEGGINGS, 4);
                        m.put(ArmorItem.Type.CHESTPLATE, 0);
                        m.put(ArmorItem.Type.HELMET, 0);
                        m.put(ArmorItem.Type.BODY, 0);
                    }),
                    0,
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(Kakugane.id("greaves")),
                            new ArmorMaterial.Layer(Kakugane.id("greaves"), "_accent", true)),
                    1.0f, 0f));

    // ---- Entities ------------------------------------------------------------------------------------
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Kakugane.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<Hound>> HOUND = ENTITIES.register("hound",
            () -> EntityType.Builder.of(Hound::new, MobCategory.MONSTER)
                    .sized(0.9f, 1.05f).clientTrackingRange(10).build("hound"));

    public static final DeferredHolder<EntityType<?>, EntityType<Ram>> RAM = ENTITIES.register("ram",
            () -> EntityType.Builder.of(Ram::new, MobCategory.MONSTER)
                    .sized(1.1f, 1.4f).clientTrackingRange(10).build("ram"));

    public static final DeferredHolder<EntityType<?>, EntityType<Crawler>> CRAWLER = ENTITIES.register("crawler",
            () -> EntityType.Builder.of(Crawler::new, MobCategory.MONSTER)
                    .sized(1.5f, 0.95f).clientTrackingRange(10).build("crawler"));

    public static final DeferredHolder<EntityType<?>, EntityType<LabLeader>> LAB_LEADER = ENTITIES.register("lab_leader",
            () -> EntityType.Builder.of(LabLeader::new, MobCategory.MONSTER)
                    .sized(0.7f, 2.05f).clientTrackingRange(10).build("lab_leader"));

    public static final DeferredHolder<EntityType<?>, EntityType<WebShot>> WEB_SHOT = ENTITIES.register("web_shot",
            () -> EntityType.Builder.<WebShot>of(WebShot::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(4).updateInterval(10).build("web_shot"));

    public static final DeferredHolder<EntityType<?>, EntityType<AlchemicArrow>> ALCHEMIC_ARROW = ENTITIES.register("alchemic_arrow",
            () -> EntityType.Builder.<AlchemicArrow>of(AlchemicArrow::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20).build("alchemic_arrow"));

    // ---- Blocks --------------------------------------------------------------------------------------
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Kakugane.MODID);

    public static final DeferredBlock<EmbryoVatBlock> EMBRYO_VAT = BLOCKS.register("embryo_vat",
            () -> new EmbryoVatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.6f)
                    .sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 6).isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)
                    // Pistons can't move vats (a moved vat would count as destroyed and confuse the lab tracker).
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    // ---- Items ---------------------------------------------------------------------------------------
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Kakugane.MODID);

    public static final DeferredItem<BlockItem> EMBRYO_VAT_ITEM = ITEMS.registerSimpleBlockItem(EMBRYO_VAT);

    public static final DeferredItem<KakuganeItem> KAKUGANE = ITEMS.register("kakugane",
            () -> new KakuganeItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<EmbryoItem> EMBRYO = ITEMS.register("homunculus_embryo",
            () -> new EmbryoItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<FormArmorItem> SILVER_SKIN_HELMET = silverSkin("silver_skin_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<FormArmorItem> SILVER_SKIN_CHESTPLATE = silverSkin("silver_skin_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<FormArmorItem> SILVER_SKIN_LEGGINGS = silverSkin("silver_skin_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<FormArmorItem> SILVER_SKIN_BOOTS = silverSkin("silver_skin_boots", ArmorItem.Type.BOOTS);
    public static final DeferredItem<FormArmorItem> GREAVES = ITEMS.register("greaves",
            () -> new FormArmorItem(Archetype.GREAVES, GREAVES_MATERIAL, ArmorItem.Type.LEGGINGS, new Item.Properties().stacksTo(1)));
    // Worn-form pieces that go in Curios slots (see core/WornForms).
    public static final DeferredItem<FormCurioItem> AERIAL_OPERATOR = curioPiece("aerial_operator", Archetype.AERIAL_OPERATOR, "head");
    public static final DeferredItem<FormCurioItem> ALICE_WINGS = curioPiece("alice_wings", Archetype.ALICE, "back");
    public static final DeferredItem<FormCurioItem> GAUNTLETS = curioPiece("gauntlets", Archetype.GAUNTLETS, "hands");
    public static final DeferredItem<FormCurioItem> MANTLE = curioPiece("mantle", Archetype.MANTLE, "back");
    public static final DeferredItem<FormCurioItem> BLADE_BELT = curioPiece("blade_belt", Archetype.BLADE_BELT, "belt");
    public static final DeferredItem<DeferredSpawnEggItem> HOUND_SPAWN_EGG = ITEMS.register("hound_spawn_egg",
            () -> new DeferredSpawnEggItem(HOUND, 0x34373D, 0xB3202A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> RAM_SPAWN_EGG = ITEMS.register("ram_spawn_egg",
            () -> new DeferredSpawnEggItem(RAM, 0x4A4640, 0xE2DCC8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CRAWLER_SPAWN_EGG = ITEMS.register("crawler_spawn_egg",
            () -> new DeferredSpawnEggItem(CRAWLER, 0x231B2A, 0x8E2DB8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> LAB_LEADER_SPAWN_EGG = ITEMS.register("lab_leader_spawn_egg",
            () -> new DeferredSpawnEggItem(LAB_LEADER, 0xE6E6E0, 0x9E1B2B, new Item.Properties()));

    private static DeferredItem<FormArmorItem> silverSkin(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new FormArmorItem(Archetype.SILVER_SKIN, SILVER_SKIN_MATERIAL, type, new Item.Properties().stacksTo(1)));
    }

    private static DeferredItem<FormCurioItem> curioPiece(String name, Archetype form, String slot) {
        return ITEMS.register(name, () -> new FormCurioItem(form, slot, new Item.Properties().stacksTo(1).fireResistant()));
    }

    // ---- Attachments: extra data stored on players ---------------------------------------------------
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kakugane.MODID);

    /** How the player fights. Saved, and kept through death. */
    public static final Supplier<AttachmentType<AffinityData>> AFFINITY = ATTACHMENTS.register("affinity",
            () -> AttachmentType.builder(AffinityData::new).serialize(AffinityData.CODEC).copyOnDeath().build());

    /** The heart Kakugane, if any. Saved, and kept through death (the drop on true death is handled by hand). */
    public static final Supplier<AttachmentType<HeartData>> HEART = ATTACHMENTS.register("heart",
            () -> AttachmentType.builder(HeartData::new).serialize(HeartData.CODEC).copyOnDeath().build());

    /** Short-lived combat state (active form cache, dash, hardening). Not saved; rebuilt on login. */
    public static final Supplier<AttachmentType<PlayerState>> STATE = ATTACHMENTS.register("state",
            () -> AttachmentType.builder(PlayerState::new).build());

    // ---- Biome modifier type (Homunculus ambient spawns read from config) -----------------------------
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Kakugane.MODID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<HomunculusSpawnModifier>> HOMUNCULUS_SPAWNS =
            BIOME_MODIFIERS.register("homunculus_spawns", () -> HomunculusSpawnModifier.CODEC);

    // ---- Lab structure ---------------------------------------------------------------------------------
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Kakugane.MODID);
    public static final DeferredHolder<StructureType<?>, StructureType<LabStructure>> LAB = STRUCTURE_TYPES.register("lab", () -> () -> LabStructure.CODEC);

    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Kakugane.MODID);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> LAB_PIECE = PIECES.register("lab_piece",
            () -> (StructurePieceType) LabPiece::new);

    // ---- Creative tab --------------------------------------------------------------------------------
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Kakugane.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.kakugane"))
            .icon(() -> new ItemStack(KAKUGANE.get()))
            .displayItems((params, out) -> {
                out.accept(KAKUGANE.get());
                out.accept(EMBRYO.get());
                out.accept(EMBRYO_VAT_ITEM.get());
                out.accept(HOUND_SPAWN_EGG.get());
                out.accept(RAM_SPAWN_EGG.get());
                out.accept(CRAWLER_SPAWN_EGG.get());
                out.accept(LAB_LEADER_SPAWN_EGG.get());
            })
            .build());

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        PIECES.register(modBus);
        ARMOR_MATERIALS.register(modBus);
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        BIOME_MODIFIERS.register(modBus);
        TABS.register(modBus);
    }

    private ModRegistry() {}
}
