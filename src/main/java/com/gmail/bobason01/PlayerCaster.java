package com.gmail.bobason01;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.SkillCaster;
import org.jetbrains.annotations.NotNull;

public final class PlayerCaster implements SkillCaster {

    private final AbstractEntity entity;
    private float power = 1.0f;
    private boolean usingDamageSkill = false;

    public PlayerCaster(@NotNull AbstractEntity entity) {
        this.entity = entity;
    }

    @Override
    public @NotNull AbstractEntity getEntity() {
        return entity;
    }

    @Override
    public float getPower() {
        return power;
    }

    public void setPower(float power) {
        this.power = power;
    }

    public boolean isActive() {
        return true;
    }

    public void remove() {
    }

    public void setUsingDamageSkill(boolean value) {
        this.usingDamageSkill = value;
    }

    public boolean isUsingDamageSkill() {
        return usingDamageSkill;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PlayerCaster other)) return false;
        return other.entity.equals(this.entity);
    }

    @Override
    public int hashCode() {
        return entity.hashCode();
    }
}
