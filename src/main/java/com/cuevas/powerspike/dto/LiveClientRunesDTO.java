package com.cuevas.powerspike.dto;

public record LiveClientRunesDTO(
        LiveClientRuneTreeDTO keystone,
        LiveClientRuneTreeDTO primaryRuneTree,
        LiveClientRuneTreeDTO secondaryRuneTree
) {}
