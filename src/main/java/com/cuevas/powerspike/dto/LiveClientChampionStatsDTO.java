package com.cuevas.powerspike.dto;

public record LiveClientChampionStatsDTO(
        double abilityHaste,
        double abilityPower,
        double armor,
        double attackDamage,
        double attackSpeed,
        double currentHealth,
        double maxHealth,
        double magicResist,
        double moveSpeed,
        double armorPenetrationPercent,
        double magicPenetrationPercent,
        double critChance,
        double lifeSteal,
        double omnivamp,
        double tenacity
) {}
