package com.example.akaishi.forbidden.forge.data;

import com.example.akaishi.forbidden.AkaishiForbiddenMod;
import com.example.akaishi.forbidden.ForbiddenItemTags;
import com.example.akaishi.forbidden.alchemy.AlchemyMaterials;
import com.example.akaishi.forbidden.brewing.BrewingItems;
import com.example.akaishi.forbidden.item.AkaishiMagicItems;
import com.example.akaishi.forbidden.ore.ForbiddenOres;
import com.example.akaishi.forbidden.wand.AkaishiWandItems;
import com.example.akaishi.forbidden.wand.WandPartDef;
import com.example.akaishi.forbidden.wand.WandPartItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/** 只生成内部分类；公共矿物与植物标签继续由原模块维护，避免同路径抢写。 */
public final class ForbiddenItemTagsProvider extends ItemTagsProvider {
    public ForbiddenItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                                     ExistingFileHelper existingFiles) {
        super(output, lookup, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()),
                AkaishiForbiddenMod.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookup) {
        tag(ForbiddenItemTags.HERBS).add(BrewingItems.MANDRAKE_ROOT.get(), BrewingItems.HENBANE.get(),
                BrewingItems.BELLADONNA.get(), BrewingItems.DATURA.get(), BrewingItems.ACONITE.get(),
                BrewingItems.FOXGLOVE.get(), BrewingItems.HELLROOT.get(), BrewingItems.COLCHICUM.get(),
                BrewingItems.YEW.get(), BrewingItems.ERGOT.get(), BrewingItems.PARASITIC_VINE.get(),
                BrewingItems.EMBER_MOSS.get(), BrewingItems.GLINT_WEED.get());
        tag(ForbiddenItemTags.CINNABAR_FRAGMENTS).add(ForbiddenOres.CINNABAR_FRAGMENT.get());
        tag(ForbiddenItemTags.SULFUR_FRAGMENTS).add(ForbiddenOres.SULFUR_FRAGMENT.get());
        tag(ForbiddenItemTags.ORE_FRAGMENTS).addTag(ForbiddenItemTags.CINNABAR_FRAGMENTS)
                .addTag(ForbiddenItemTags.SULFUR_FRAGMENTS);
        parts(ForbiddenItemTags.WAND_TIPS, WandPartDef.Kind.TIP);
        parts(ForbiddenItemTags.WAND_HANDLES, WandPartDef.Kind.HANDLE);
        parts(ForbiddenItemTags.WAND_CORES, WandPartDef.Kind.CORE);
        tag(ForbiddenItemTags.WAND_PARTS).addTag(ForbiddenItemTags.WAND_TIPS)
                .addTag(ForbiddenItemTags.WAND_HANDLES).addTag(ForbiddenItemTags.WAND_CORES);
        tag(ForbiddenItemTags.WANDS).add(AkaishiWandItems.wand.get());
        tag(ForbiddenItemTags.INKS).add(AkaishiMagicItems.inks().values().stream()
                .map(supplier -> supplier.get()).toArray(Item[]::new));
        tag(ForbiddenItemTags.BOTTLED).addTag(ForbiddenItemTags.INKS)
                .add(BrewingItems.SLAKED_LIME_BOTTLE.get(), BrewingItems.POTION.get(), BrewingItems.FUME.get())
                .add(AlchemyMaterials.MATERIALS.stream().filter(material -> material.shape().equals("bottle"))
                        .map(material -> AlchemyMaterials.item(material.name())).toArray(Item[]::new));
        tag(ForbiddenItemTags.MAGIC_CORES).add(AkaishiMagicItems.cores().values().stream()
                .map(supplier -> supplier.get()).toArray(Item[]::new));
    }

    private void parts(TagKey<Item> key, WandPartDef.Kind kind) {
        tag(key).add(AkaishiWandItems.parts().values().stream().map(supplier -> supplier.get())
                .filter(item -> item instanceof WandPartItem part && part.def().kind() == kind)
                .toArray(Item[]::new));
    }
}
