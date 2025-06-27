package com.shmuelzon.HomeAssistantFloorPlan;

import java.util.Objects;

public class HaEntity implements Comparable<HaEntity> {
    private final String entityId;
    private final String friendlyName;
    private final String domain;
    private final String areaName;

    public HaEntity(String entityId, String friendlyName, String areaName) {
        this.entityId = entityId;
        this.friendlyName = friendlyName;
        this.areaName = areaName;
        if (entityId != null && entityId.contains(".")) {
            this.domain = entityId.substring(0, entityId.indexOf('.'));
        } else {
            this.domain = "unknown";
        }
    }

    public String getEntityId() {
        return entityId;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getDomain() {
        return domain;
    }

    public String getAreaName() {
        return areaName;
    }

    @Override
    public String toString() {
        if (areaName != null && !areaName.isEmpty()) {
            return String.format("%s: %s (%s)", areaName, friendlyName, entityId);
        }
        return friendlyName + " (" + entityId + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HaEntity haEntity = (HaEntity) o;
        return Objects.equals(entityId, haEntity.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }

    @Override
    public int compareTo(HaEntity other) {
        // Use a prefix for "No Area" to ensure it sorts last alphabetically.
        String thisArea = this.getAreaName() == null || this.getAreaName().trim().isEmpty()
                        ? "zzz_No Area"
                        : this.getAreaName();
        String otherArea = other.getAreaName() == null || other.getAreaName().trim().isEmpty()
                         ? "zzz_No Area"
                         : other.getAreaName();

        // 1. Primary sort by Area Name (case-insensitive)
        int areaCompare = thisArea.compareToIgnoreCase(otherArea);
        if (areaCompare != 0) {
            return areaCompare;
        }

        // 2. Secondary sort by Domain (case-insensitive)
        int domainCompare = this.getDomain().compareToIgnoreCase(other.getDomain());
        if (domainCompare != 0) {
            return domainCompare;
        }

        // 3. Tertiary sort by Friendly Name (case-insensitive)
        return this.getFriendlyName().compareToIgnoreCase(other.getFriendlyName());
    }
}