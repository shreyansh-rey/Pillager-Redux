package com.yan.projectpillagers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class DestroyedOutpostsData extends SavedData {

    public static final Codec<DestroyedOutpostsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BoundingBox.CODEC.listOf().fieldOf("destroyed_outposts").forGetter(d -> d.destroyedOutposts)
    ).apply(instance, DestroyedOutpostsData::new));

    public static final SavedDataType<DestroyedOutpostsData> TYPE = new SavedDataType<DestroyedOutpostsData>(
            Identifier.fromNamespaceAndPath("projectpillagers", "destroyed_outposts"),
            () -> new DestroyedOutpostsData(new ArrayList<>()),
            CODEC,
            null
    );

    public final List<BoundingBox> destroyedOutposts;

    private DestroyedOutpostsData(List<BoundingBox> destroyedOutposts) {
        this.destroyedOutposts = new ArrayList<>(destroyedOutposts);
    }

    public boolean isDestroyed(BoundingBox box) {
        Debug.Log("Bounding box exists: " + destroyedOutposts.contains(box));
        return destroyedOutposts.contains(box);
    }

    public void markDestroyed(BoundingBox box) {
        if (!isDestroyed(box)) {
            Debug.Log("Bounding box added: " + box);
            destroyedOutposts.add(box);
            setDirty();
        }
    }
}