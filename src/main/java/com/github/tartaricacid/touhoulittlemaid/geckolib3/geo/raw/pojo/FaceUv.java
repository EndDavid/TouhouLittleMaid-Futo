package com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;


public class FaceUv implements Serializable {
    @SerializedName("material_instance")
    private String materialInstance;
    @SerializedName("uv")
    private double[] uv;
    @SerializedName("uv_size")
    private double[] uvSize;
    /** 贴图矩形的旋转角（0 / 90 / 180 / 270）。基岩格式里几乎只用 180。 */
    @SerializedName("uv_rotation")
    private Integer uvRotation;

    public String getMaterialInstance() {
        return materialInstance;
    }

    public void setMaterialInstance(String value) {
        this.materialInstance = value;
    }

    public double[] getUv() {
        return uv;
    }

    public void setUv(double[] value) {
        this.uv = value;
    }

    public double[] getUvSize() {
        return uvSize;
    }

    public Integer getUvRotation() {
        return uvRotation;
    }

    public void setUvRotation(Integer value) {
        this.uvRotation = value;
    }

    public void setUvSize(double[] value) {
        this.uvSize = value;
    }
}
