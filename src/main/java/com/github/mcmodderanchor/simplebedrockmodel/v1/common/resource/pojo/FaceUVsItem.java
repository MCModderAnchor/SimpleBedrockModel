package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

public class FaceUVsItem {
    @SerializedName("down")
    private FaceItem down;
    @SerializedName("east")
    private FaceItem east;
    @SerializedName("north")
    private FaceItem north;
    @SerializedName("south")
    private FaceItem south;
    @SerializedName("up")
    private FaceItem up;
    @SerializedName("west")
    private FaceItem west;

    public FaceUVsItem(FaceItem down, FaceItem east, FaceItem north, FaceItem south, FaceItem up, FaceItem west) {
        this.down = down;
        this.east = east;
        this.north = north;
        this.south = south;
        this.up = up;
        this.west = west;
    }

    @Nullable
    public FaceItem getFace(Direction direction) {
        return switch (direction) {
            case EAST -> east;
            case WEST -> west;
            case NORTH -> north;
            case SOUTH -> south;
            case UP -> up;
            default -> down;
        };
    }
}
